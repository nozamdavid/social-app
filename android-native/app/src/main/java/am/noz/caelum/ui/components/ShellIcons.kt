package am.noz.caelum.ui.components

import androidx.compose.ui.graphics.PathFillType

/** Bluesky shell glyphs from src/components/icons. */
internal object ShellIcons {
    val Home = bskyVector(
        "HomeOpen_Stroke2_Corner0_Rounded",
        "M19 18V9.908a1 1 0 0 0-.342-.753l-6-5.25a1 1 0 0 0-1.316 0l-6 5.25A1 1 0 0 0 5 9.908V18a1 1 0 0 0 1 1h3v-3a3 3 0 1 1 6 0v3h3a1 1 0 0 0 1-1Zm2 0a3 3 0 0 1-3 3h-3a2 2 0 0 1-2-2v-3a1 1 0 1 0-2 0v3a2 2 0 0 1-2 2H6a3 3 0 0 1-3-3V9.908A3 3 0 0 1 4.024 7.65l6-5.25a3 3 0 0 1 3.952 0l6 5.25A3 3 0 0 1 21 9.908V18Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val HomeSelected = bskyVector(
        "HomeOpen_Filled_Corner0_Rounded",
        "M13.976 2.4a3 3 0 0 0-3.952 0l-6 5.25A3 3 0 0 0 3 9.907V18a3 3 0 0 0 3 3h2a2 2 0 0 0 2-2v-3a2 2 0 0 1 4 0v3a2 2 0 0 0 2 2h2a3 3 0 0 0 3-3V9.907a3 3 0 0 0-1.024-2.257l-6-5.25Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val Search = bskyVector(
        "MagnifyingGlass_Stroke2_Corner0_Rounded",
        "M11 5a6 6 0 1 0 0 12 6 6 0 0 0 0-12Zm-8 6a8 8 0 1 1 14.32 4.906l3.387 3.387a1 1 0 0 1-1.414 1.414l-3.387-3.387A8 8 0 0 1 3 11Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val SearchSelected = bskyVector(
        "MagnifyingGlass_Filled_Stroke2_Corner0_Rounded",
        "M5 11a6 6 0 1 1 12 0 6 6 0 0 1-12 0Zm6-8a8 8 0 1 0 4.906 14.32l3.387 3.387a1 1 0 0 0 1.414-1.414l-3.387-3.387A8 8 0 0 0 11 3Zm4 8a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val Chat = bskyVector(
        "Message_Stroke2_Corner0_Rounded",
        "M19.55 12a7.55 7.55 0 1 0-14.33 3.329c.142.287.193.623.125.957l-.618 3.007 3.131-.585.12-.018c.277-.029.552.022.793.137h0A7.5 7.5 0 0 0 12 19.55 7.55 7.55 0 0 0 19.55 12Zm2 0a9.55 9.55 0 0 1-13.48 8.703l-3.741.7a1.476 1.476 0 0 1-1.717-1.746l.741-3.608A9.55 9.55 0 1 1 21.55 12Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val ChatSelected = bskyVector(
        "Message_Stroke2_Corner0_Rounded_Filled",
        "M21.55 12a9.55 9.55 0 0 1-13.48 8.703l-3.741.7a1.476 1.476 0 0 1-1.717-1.746l.741-3.608A9.55 9.55 0 1 1 21.55 12Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val Notifications = bskyVector(
        "Bell_Stroke2_Corner0_Rounded",
        "M12 2a7.31 7.31 0 0 1 7.299 6.942l.178 3.588q.012.21.105.398l1.227 2.454.083.193a1.81 1.81 0 0 1-1.7 2.425h-2.293a5.001 5.001 0 0 1-9.798 0H4.809a1.81 1.81 0 0 1-1.8-1.624L3 16.191l.012-.21a1.8 1.8 0 0 1 .18-.6l1.226-2.453a1 1 0 0 0 .104-.397l.18-3.589A7.31 7.31 0 0 1 12 2ZM9.174 18c.412 1.165 1.52 2 2.826 2a3 3 0 0 0 2.826-2H9.174ZM12 4a5.307 5.307 0 0 0-5.3 5.042l-.18 3.589a3 3 0 0 1-.313 1.191L5.118 16h13.764l-1.089-2.178a3 3 0 0 1-.3-1.036l-.012-.155-.18-3.589A5.307 5.307 0 0 0 12 4Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val NotificationsSelected = bskyVector(
        "Bell_Filled_Corner0_Rounded",
        "M12 2a7.307 7.307 0 0 0-7.298 6.943l-.18 3.588a1 1 0 0 1-.104.397L3.19 15.382A1.81 1.81 0 0 0 4.809 18H7.1a5.002 5.002 0 0 0 9.8 0h2.291a1.81 1.81 0 0 0 1.618-2.618l-1.227-2.454a1 1 0 0 1-.104-.397l-.18-3.588A7.31 7.31 0 0 0 12 2Zm0 18a3 3 0 0 1-2.83-2h5.66A3 3 0 0 1 12 20Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val Compose = bskyVector(
        "EditBig_Stroke2_Corner2_Rounded",
        "M3 16.8V7.2c0-.544-.001-1.011.03-1.395.033-.395.104-.789.297-1.167a3 3 0 0 1 1.31-1.31c.379-.193.772-.265 1.168-.297C6.188 2.999 6.657 3 7.2 3H11a1 1 0 1 1 0 2H7.2c-.576 0-.949 0-1.232.023-.272.022-.373.06-.422.085a1 1 0 0 0-.437.437c-.025.05-.062.15-.085.422C5.001 6.251 5 6.623 5 7.2v9.6c0 .577.001.95.024 1.232.023.272.06.373.085.422a1 1 0 0 0 .437.437c.05.025.15.063.422.085.283.023.656.024 1.232.024h9.6c.576 0 .949-.001 1.232-.024.272-.022.373-.06.422-.085a1 1 0 0 0 .437-.437c.025-.049.062-.15.085-.422.023-.283.024-.655.024-1.232V13a1 1 0 1 1 2 0v3.8c0 .543.001 1.011-.03 1.395-.033.395-.104.788-.297 1.167a3 3 0 0 1-1.31 1.311c-.379.193-.772.264-1.168.296-.383.031-.852.031-1.395.031H7.2c-.543 0-1.012 0-1.395-.031-.396-.032-.789-.103-1.167-.296a3 3 0 0 1-1.31-1.311c-.194-.379-.265-.772-.298-1.167C3 17.81 3 17.343 3 16.8M16.629 2.957a3 3 0 0 1 4.242 0l.172.171a3 3 0 0 1 0 4.243L13 15.414a2 2 0 0 1-1.414.586H9a1 1 0 0 1-1-1v-2.586A2 2 0 0 1 8.586 11zM10 14h1.586l8.043-8.043a1 1 0 0 0 0-1.414l-.172-.172a1 1 0 0 0-1.414 0L10 12.414z",
        pathFillType = PathFillType.EvenOdd,
    )

    val Back = bskyVector(
        "ArrowLeft_Stroke2_Corner0_Rounded",
        "M3 12a1 1 0 0 1 .293-.707l6-6a1 1 0 0 1 1.414 1.414L6.414 11H20a1 1 0 1 1 0 2H6.414l4.293 4.293a1 1 0 0 1-1.414 1.414l-6-6A1 1 0 0 1 3 12Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val Share = bskyVector(
        "ArrowOutOfBoxModified_Stroke2_Corner2_Rounded",
        "M20 13.75a1 1 0 0 1 1 1V18a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3v-3.25a1 1 0 1 1 2 0V18a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-3.25a1 1 0 0 1 1-1ZM12 3a1 1 0 0 1 .707.293l4.5 4.5a1 1 0 1 1-1.414 1.414L13 6.414v8.836a1 1 0 1 1-2 0V6.414L8.207 9.207a1 1 0 1 1-1.414-1.414l4.5-4.5A1 1 0 0 1 12 3Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val More = bskyVector(
        "DotGrid3x1_Stroke2_Corner0_Rounded",
        "M2 12a2 2 0 1 1 4 0 2 2 0 0 1-4 0Zm16 0a2 2 0 1 1 4 0 2 2 0 0 1-4 0Zm-6-2a2 2 0 1 0 0 4 2 2 0 0 0 0-4Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val Like = bskyVector(
        "Heart2_Filled_Stroke2_Corner0_Rounded",
        "M12.489 21.372c8.528-4.78 10.626-10.47 9.022-14.47-.779-1.941-2.414-3.333-4.342-3.763-1.697-.378-3.552.003-5.169 1.287-1.617-1.284-3.472-1.665-5.17-1.287-1.927.43-3.562 1.822-4.34 3.764-1.605 4 .493 9.69 9.021 14.47a1 1 0 0 0 .978 0Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val Pin = bskyVector(
        "Pin_Stroke2_Corner0_Rounded",
        "M6.5 3a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v3.997a6.25 6.25 0 0 0 1.83 4.42l.377.376A1 1 0 0 1 20 12.5V15a1 1 0 0 1-1 1h-6v5a1 1 0 1 1-2 0v-5H5a1 1 0 0 1-1-1v-2.5a1 1 0 0 1 .293-.707l.376-.377A6.25 6.25 0 0 0 6.5 6.996V3.001Zm2 1v2.997a8.25 8.25 0 0 1-2.416 5.834L6 12.914V14h12v-1.086l-.084-.083A8.25 8.25 0 0 1 15.5 6.997V4h-7Z",
        pathFillType = PathFillType.EvenOdd,
    )

    val PinSelected = bskyVector(
        "Pin_Filled_Corner0_Rounded",
        "M7.5 2a1 1 0 0 0-1 1v3.997a6.25 6.25 0 0 1-1.83 4.42l-.377.376A1 1 0 0 0 4 12.5V15a1 1 0 0 0 1 1h6v5a1 1 0 1 0 2 0v-5h6a1 1 0 0 0 1-1v-2.5a1 1 0 0 0-.293-.707l-.376-.377a6.25 6.25 0 0 1-1.831-4.42V3.001a1 1 0 0 0-1-1h-9Z",
        pathFillType = PathFillType.EvenOdd,
    )
}
