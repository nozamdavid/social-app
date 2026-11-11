package am.noz.caelum.alttext

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val MAX_IMAGE_EDGE = 2048

class CaelumAltTextModule : Module() {
  private class Request(val id: String) {
    val cancelled = AtomicBoolean(false)
  }

  private class RequestFailure(val code: String) : Exception(code)

  private val worker = Executors.newFixedThreadPool(2)
  private val currentRequest = AtomicReference<Request?>(null)
  private val destroyed = AtomicBoolean(false)
  private val foreground = AtomicBoolean(true)

  override fun definition() = ModuleDefinition {
    Name("CaelumAltText")
    Events("onProgress")

    OnActivityEntersForeground { foreground.set(true) }
    OnActivityEntersBackground { foreground.set(false) }

    AsyncFunction("getDescriptionStatus") { promise: Promise ->
      worker.execute {
        try {
          val client = ImageDescription.getClient(ImageDescriberOptions.builder(context()).build())
          try {
            val future = client.checkFeatureStatus()
            val status = try {
              future.get(20, TimeUnit.SECONDS)
            } finally {
              future.cancel(false)
            }
            promise.resolve(statusName(status))
          } finally {
            client.close()
          }
        } catch (error: Exception) {
          reject(promise, error)
        }
      }
    }

    AsyncFunction("generate") { id: String, method: String, uri: String, allowDownload: Boolean, promise: Promise ->
      val request = Request(id)
      if (!currentRequest.compareAndSet(null, request)) {
        promise.reject("ERR_BUSY", "Another image operation is still finishing.", null)
        return@AsyncFunction
      }
      worker.execute {
        try {
          ensureActive(request)
          val text = when (method) {
            "ocr" -> recognize(request, uri)
            "describe" -> describe(request, uri, allowDownload)
            else -> throw RequestFailure("ERR_INVALID_REQUEST")
          }
          ensureActive(request)
          promise.resolve(text.trim())
        } catch (error: Exception) {
          reject(promise, error)
        } finally {
          currentRequest.compareAndSet(request, null)
        }
      }
    }

    AsyncFunction("cancel") { id: String ->
      currentRequest.get()?.takeIf { it.id == id }?.cancelled?.set(true)
    }

    OnDestroy {
      destroyed.set(true)
      currentRequest.get()?.cancelled?.set(true)
      /*
       * ML Kit does not guarantee cancellation of service work. Let pending
       * operations finish before releasing their image buffers and clients.
       */
      worker.shutdown()
    }
  }

  private fun context() = appContext.reactContext?.applicationContext
    ?: throw RequestFailure("ERR_UNAVAILABLE")

  private fun statusName(status: Int) = when (status) {
    FeatureStatus.AVAILABLE -> "available"
    FeatureStatus.DOWNLOADABLE -> "downloadable"
    FeatureStatus.DOWNLOADING -> "downloading"
    else -> "unavailable"
  }

  private fun ensureActive(request: Request) {
    if (destroyed.get() || request.cancelled.get()) throw CancellationException()
    if (!foreground.get() || appContext.currentActivity == null) {
      throw RequestFailure("ERR_BACKGROUND")
    }
  }

  private fun progress(request: Request, phase: String, downloaded: Long = 0, total: Long = 0) {
    if (!destroyed.get() && !request.cancelled.get()) {
      sendEvent("onProgress", mapOf(
        "requestId" to request.id,
        "phase" to phase,
        "downloadedBytes" to downloaded.toDouble(),
        "totalBytes" to total.toDouble(),
      ))
    }
  }

  private fun recognize(request: Request, uri: String): String {
    val bitmap = loadImage(uri)
    try {
      ensureActive(request)
      val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      try {
        progress(request, "recognizing")
        return Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0))).text
      } finally {
        client.close()
      }
    } finally {
      bitmap.recycle()
    }
  }

  private fun describe(request: Request, uri: String, allowDownload: Boolean): String {
    val client = ImageDescription.getClient(ImageDescriberOptions.builder(context()).build())
    try {
      progress(request, "checking")
      val status = client.checkFeatureStatus().get()
      if (status == FeatureStatus.UNAVAILABLE) throw RequestFailure("ERR_UNAVAILABLE")
      if (status != FeatureStatus.AVAILABLE) {
        if (!allowDownload) throw RequestFailure("ERR_MODEL_REQUIRED")
        var total = 0L
        progress(request, "downloading")
        client.downloadFeature(object : DownloadCallback {
          override fun onDownloadStarted(bytesToDownload: Long) {
            total = bytesToDownload
            progress(request, "downloading", total = total)
          }
          override fun onDownloadProgress(totalBytesDownloaded: Long) {
            progress(request, "downloading", totalBytesDownloaded, total)
          }
          override fun onDownloadCompleted() {}
          override fun onDownloadFailed(e: GenAiException) {}
        }).get()
      }
      ensureActive(request)
      val bitmap = loadImage(uri)
      try {
        ensureActive(request)
        progress(request, "describing")
        return client.runInference(ImageDescriptionRequest.builder(bitmap).build()).get().description
      } finally {
        bitmap.recycle()
      }
    } finally {
      client.close()
    }
  }

  /** Decode local images at a bounded size and preserve their EXIF orientation. */
  private fun loadImage(path: String): Bitmap {
    val parsed = Uri.parse(path)
    val uri = if (parsed.scheme == null && path.startsWith("/")) Uri.fromFile(File(path)) else parsed
    if (uri.scheme != "file" && uri.scheme != "content") throw RequestFailure("ERR_IMAGE")
    val resolver = context().contentResolver
    fun open() = resolver.openInputStream(uri) ?: throw RequestFailure("ERR_IMAGE")
    try {
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      open().use { BitmapFactory.decodeStream(it, null, options) }
      if (options.outWidth <= 0 || options.outHeight <= 0) throw RequestFailure("ERR_IMAGE")
      options.inSampleSize = 1
      while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > MAX_IMAGE_EDGE) {
        options.inSampleSize *= 2
      }
      options.inJustDecodeBounds = false
      val orientation = open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
      val bitmap = open().use { BitmapFactory.decodeStream(it, null, options) } ?: throw RequestFailure("ERR_IMAGE")
      val matrix = Matrix().apply {
        when (orientation) {
          ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
          ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
          ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
          ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
          ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
          ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
          ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
      }
      if (matrix.isIdentity) return bitmap
      return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it !== bitmap) bitmap.recycle()
      }
    } catch (error: RequestFailure) {
      throw error
    } catch (error: Exception) {
      throw RequestFailure("ERR_IMAGE")
    }
  }

  private fun reject(promise: Promise, error: Exception) {
    var cause: Throwable = error
    while (cause is ExecutionException && cause.cause != null) cause = cause.cause!!
    val code = when (cause) {
      is RequestFailure -> cause.code
      is CancellationException -> "ERR_CANCELLED"
      is TimeoutException -> "ERR_TIMEOUT"
      is GenAiException -> when (cause.errorCode) {
        GenAiException.ErrorCode.BUSY -> "ERR_BUSY"
        GenAiException.ErrorCode.NOT_AVAILABLE -> "ERR_UNAVAILABLE"
        GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> "ERR_STORAGE"
        GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE, GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> "ERR_SYSTEM_UPDATE"
        else -> "ERR_GENERATION"
      }
      else -> "ERR_GENERATION"
    }
    promise.reject(code, code, null)
  }
}
