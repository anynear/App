package app.anynear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

/**
 * The emblem of anynear: two offset bands, top left in [arch] (petrol on a light, white on a dark background),
 * bottom right in orange. With a tile on petrol (avatars, empty states), then the upper band is white.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 72.dp, withTile: Boolean = true, arch: Color = AppTheme.colors.onHeader) {
    val mark = remember(withTile, arch) { anynearMark(withTile, arch) }
    Image(rememberVectorPainter(mark), contentDescription = null, modifier = modifier.size(size))
}

// Paths as in Docs/marke/assets/anynear-emblem.svg (version 08.11.2026). Whoever changes them changes both places.
private const val AnynearUpper = "M678.891 327.152 Q677.835 322.927 648.259 292.295 Q618.684 261.663 618.684 260.607 Q618.684 259.550 604.952 244.762 Q591.221 229.975 576.433 219.412 Q561.645 208.849 542.632 202.511 Q523.619 196.174 505.662 196.174 Q487.706 196.174 486.649 197.230 Q485.593 198.286 480.312 198.286 Q475.030 198.286 473.974 199.343 Q472.918 200.399 469.749 200.399 Q466.580 200.399 457.074 203.568 Q447.567 206.737 439.117 210.962 Q430.667 215.187 419.048 223.637 Q407.429 232.087 402.147 238.425 Q396.866 244.762 341.940 298.632 Q287.013 352.502 283.845 356.728 Q280.676 360.953 279.619 360.953 Q278.563 360.953 227.862 411.654 Q177.161 462.355 173.992 464.468 Q170.823 466.580 162.373 478.199 Q153.923 489.818 149.698 501.437 Q145.473 513.056 145.473 517.281 Q145.473 521.506 144.416 522.563 Q143.360 523.619 143.360 534.182 Q143.360 544.745 144.416 545.801 Q145.473 546.857 145.473 551.082 Q145.473 555.307 146.529 556.364 Q147.585 557.420 148.641 563.757 Q149.698 570.095 154.979 579.602 Q160.260 589.108 168.711 598.615 Q177.161 608.121 187.724 615.515 Q198.286 622.909 202.511 623.965 Q206.737 625.021 209.905 627.134 Q213.074 629.247 219.412 630.303 Q225.749 631.359 226.806 632.415 Q227.862 633.472 233.143 633.472 Q238.425 633.472 239.481 634.528 Q240.537 635.584 251.100 635.584 Q261.663 635.584 262.719 634.528 Q263.775 633.472 269.057 633.472 Q274.338 633.472 283.845 630.303 Q293.351 627.134 301.801 622.909 Q310.251 618.684 319.758 611.290 Q329.264 603.896 335.602 596.502 Q341.940 589.108 342.996 589.108 Q344.052 589.108 396.866 536.294 Q449.680 483.481 450.736 483.481 Q451.792 483.481 510.944 425.385 Q570.095 367.290 579.602 360.953 Q589.108 354.615 602.840 348.277 Q616.571 341.940 619.740 341.940 Q622.909 341.940 627.134 339.827 Q631.359 337.715 636.640 337.715 Q641.922 337.715 642.978 336.658 Q644.034 335.602 659.879 335.602 Q675.723 335.602 677.835 333.490 Q679.948 331.377 678.891 327.152 Z"
private const val AnynearLower = "M876.415 453.905 Q874.302 447.567 867.965 437.004 Q861.627 426.442 852.121 416.935 Q842.614 407.429 832.051 401.091 Q821.489 394.753 815.151 392.641 Q808.813 390.528 805.644 390.528 Q802.476 390.528 801.419 389.472 Q800.363 388.416 785.575 387.360 Q770.787 386.303 769.731 387.360 Q768.675 388.416 758.112 389.472 Q747.549 390.528 744.381 392.641 Q741.212 394.753 735.930 395.810 Q730.649 396.866 714.805 407.429 Q698.961 417.992 588.052 527.844 Q477.143 637.697 470.805 641.922 Q464.468 646.147 459.186 651.428 Q453.905 656.710 443.342 664.104 Q432.779 671.498 420.104 677.835 Q407.429 684.173 401.091 685.229 Q394.753 686.285 393.697 687.342 Q392.641 688.398 388.416 688.398 Q384.191 688.398 383.134 689.454 Q382.078 690.510 364.121 690.510 Q346.165 690.510 344.052 692.623 Q341.940 694.736 341.940 696.848 Q341.940 698.961 342.996 701.073 Q344.052 703.186 346.165 704.242 Q348.277 705.298 395.810 753.887 Q443.342 802.476 449.680 806.701 Q456.017 810.926 466.580 816.207 Q477.143 821.489 483.481 823.601 Q489.818 825.714 494.043 825.714 Q498.268 825.714 499.325 826.770 Q500.381 827.826 521.506 827.826 Q542.632 827.826 543.688 826.770 Q544.745 825.714 550.026 825.714 Q555.307 825.714 564.814 822.545 Q574.320 819.376 592.277 808.813 Q610.234 798.251 734.874 673.610 Q859.514 548.970 865.852 538.407 Q872.190 527.844 872.190 525.732 Q872.190 523.619 875.359 517.281 Q878.527 510.944 878.527 506.719 Q878.527 502.494 879.584 501.437 Q880.640 500.381 880.640 484.537 Q880.640 468.693 879.584 467.636 Q878.527 466.580 878.527 463.411 Q878.527 460.243 876.415 453.905 Z"
// Outline of the paths in the coordinates of the SVG (1024 canvas).
private const val AnynearLeft = 143.36f
private const val AnynearTop = 196.174f
private const val AnynearWidth = 737.28f
private const val AnynearHeight = 631.652f

private fun ImageVector.Builder.anynearEmblem(arch: Color) {
    group(translationX = -AnynearLeft, translationY = -AnynearTop) {
        addPath(pathData = addPathNodes(AnynearUpper), fill = SolidColor(arch))
        addPath(pathData = addPathNodes(AnynearLower), fill = SolidColor(BrandPalette.Signalorange))
    }
}

/** Emblem in the 72 tile, 44 units wide. Without a tile square, centred. */
private fun anynearMark(withTile: Boolean, arch: Color): ImageVector =
    ImageVector.Builder("AnynearMark", 72.dp, 72.dp, 72f, 72f).apply {
        if (withTile) {
            addPath(fill = SolidColor(BrandPalette.Nachtblau), pathData = addPathNodes("M18 0 H54 A18 18 0 0 1 72 18 V54 A18 18 0 0 1 54 72 H18 A18 18 0 0 1 0 54 V18 A18 18 0 0 1 18 0 Z"))
        }
        val width = if (withTile) 44f else 60f
        val scale = width / AnynearWidth
        group(scaleX = scale, scaleY = scale, translationX = (72f - width) / 2f, translationY = (72f - AnynearHeight * scale) / 2f) {
            anynearEmblem(if (withTile) Color.White else arch)
        }
    }.build()

/** Is shown directly below the header area, e.g. the hint about the radio connection. */
internal val LocalBelowHeader = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/** Sits in the header area to the left of the flame, e.g. whether and with which radio the app is connected. */
internal val LocalHeaderStatus = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * Running background tasks (e.g. the search for documents): in the header of every page, so that one sees that
 * something is running and gets there with one tap. `null` as long as nothing is running.
 */
internal val LocalHeaderTasks = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/** ⋮ menu at the far right in the header of every main page (ADR 0062 §1): everywhere in the same place, with the same entries. */
internal val LocalHeaderMenu = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * Header area of every main screen, light like the surface below it (anynear design). [onTitleClick] makes picture and title tappable (e.g. in the
 * chat to the detail page of the circle), [trailing] takes actions on the right (e.g. share).
 */
@Composable
internal fun AppHeader(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    /** Picture of the circle or the person in front of the title. */
    avatar: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    /** What tapping the title does, for screen readers. */
    titleClickLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** Main page of a tab: only there radio and menu, so that chats and subpages have room for the title. */
    main: Boolean = leading == null,
) {
    val colors = AppTheme.colors
    Surface(color = colors.header, contentColor = colors.onHeader, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leading?.invoke()
            Row(
                Modifier.weight(1f).then(if (onTitleClick != null) Modifier.clickable(onClickLabel = titleClickLabel, onClick = onTitleClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                avatar?.invoke()
                Column(Modifier.weight(1f)) {
                    // Hyphenate long names at syllable boundaries with large font instead of in the middle of the word.
                    // Main pages with a large title as in the design, chats and subpages more compact.
                    val titleStyle = if (main) MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleLarge
                    val titleText = @Composable { modifier: Modifier ->
                        Text(title, style = titleStyle.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), modifier = modifier.semantics { heading() })
                    }
                    if (onTitleClick != null) {
                        // Same arrow as on rows that lead on: shows that tapping the title opens the page of the circle,
                        // contact or channel, everywhere where that is possible.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            titleText(Modifier.weight(1f, fill = false))
                            Chevron(Modifier.padding(start = 2.dp), tint = colors.headerAccent)
                        }
                    } else {
                        titleText(Modifier)
                    }
                    subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph), color = colors.headerAccent) }
                }
            }
            LocalHeaderTasks.current?.invoke()
            trailing?.invoke()
            if (main) {
                // No emblem in the header: app icon and start screen carry the brand, the header stays for title and state.
                LocalHeaderStatus.current?.invoke()
                LocalHeaderMenu.current?.invoke()
            }
        }
    }
    LocalBelowHeader.current?.invoke()
}

/** Own line icons in the style of the logo (2 dp stroke, round ends). No emoji in the interface. */
internal object AppIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.line(d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = d,
        )

    private fun ImageVector.Builder.dot(cx: Float, cy: Float, r: Float) =
        path(fill = SolidColor(Color.Black)) {
            moveTo(cx - r, cy)
            arcToRelative(r, r, 0f, true, false, 2 * r, 0f)
            arcToRelative(r, r, 0f, true, false, -2 * r, 0f)
            close()
        }

    /** „Mein Kreis“: ring with three people. */
    val Kreis: ImageVector = icon("Kreis") {
        line {
            moveTo(4f, 13f)
            arcToRelative(8f, 8f, 0f, true, false, 16f, 0f)
            arcToRelative(8f, 8f, 0f, true, false, -16f, 0f)
        }
        dot(12f, 5f, 2.6f)
        dot(5.1f, 17f, 2.2f)
        dot(18.9f, 17f, 2.2f)
    }

    /** Contacts: person. */
    val Person: ImageVector = icon("Person") {
        line {
            moveTo(8.5f, 8f)
            arcToRelative(3.5f, 3.5f, 0f, true, false, 7f, 0f)
            arcToRelative(3.5f, 3.5f, 0f, true, false, -7f, 0f)
        }
        line {
            moveTo(5f, 20f)
            curveToRelative(0.6f, -3.6f, 3.4f, -6f, 7f, -6f)
            reflectiveCurveToRelative(6.4f, 2.4f, 7f, 6f)
        }
    }

    /** Radio with antenna. */
    val Funkgeraet: ImageVector = icon("Funkgeraet") {
        line {
            moveTo(8f, 9f)
            horizontalLineToRelative(8f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineToRelative(8f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineToRelative(-8f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineToRelative(-8f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
        }
        line {
            moveTo(9f, 9f)
            lineTo(9f, 3f)
        }
        line {
            moveTo(9.5f, 13f)
            horizontalLineToRelative(5f)
        }
        dot(12f, 17.5f, 1.4f)
    }

    /** Send: paper plane. */
    val Senden: ImageVector = icon("Senden") {
        line {
            moveTo(4f, 12f)
            lineTo(20f, 4f)
            lineTo(15f, 20f)
            lineTo(11f, 13f)
            close()
        }
        line {
            moveTo(11f, 13f)
            lineTo(20f, 4f)
        }
    }

    /** Voice message: microphone (ADR 0030). */
    val Mikrofon: ImageVector = icon("Mikrofon") {
        line {
            moveTo(9f, 6f)
            arcToRelative(3f, 3f, 0f, false, true, 6f, 0f)
            verticalLineTo(11f)
            arcToRelative(3f, 3f, 0f, false, true, -6f, 0f)
            close()
        }
        line {
            moveTo(5.5f, 11f)
            arcToRelative(6.5f, 6.5f, 0f, false, false, 13f, 0f)
        }
        line {
            moveTo(12f, 17.5f)
            verticalLineTo(21f)
        }
    }

    /** Play: triangle. */
    val Abspielen: ImageVector = icon("Abspielen") {
        line {
            moveTo(7f, 4.5f)
            lineTo(19f, 12f)
            lineTo(7f, 19.5f)
            close()
        }
    }

    /** Pause: two bars. */
    val Anhalten: ImageVector = icon("Anhalten") {
        line {
            moveTo(8f, 5f)
            verticalLineTo(19f)
        }
        line {
            moveTo(16f, 5f)
            verticalLineTo(19f)
        }
    }

    /** Attachment: paper clip. */
    val Anhang: ImageVector = icon("Anhang") {
        line {
            moveTo(21.4f, 11.1f)
            lineToRelative(-9.2f, 9.2f)
            arcToRelative(6f, 6f, 0f, false, true, -8.5f, -8.5f)
            lineToRelative(9.2f, -9.2f)
            arcToRelative(4f, 4f, 0f, false, true, 5.7f, 5.7f)
            lineToRelative(-9.2f, 9.2f)
            arcToRelative(2f, 2f, 0f, false, true, -2.8f, -2.8f)
            lineToRelative(8.5f, -8.5f)
        }
    }

    /** Photo: frame with sun and mountain. */
    val Bild: ImageVector = icon("Bild") {
        line {
            moveTo(5f, 3f)
            horizontalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
        }
        dot(8.5f, 8.5f, 1.5f)
        line {
            moveTo(21f, 15f)
            lineTo(16f, 10f)
            lineTo(5f, 21f)
        }
    }

    /** PDF: sheet with dog-ear and lines. */
    val Dokument: ImageVector = icon("Dokument") {
        line {
            moveTo(14f, 2f)
            horizontalLineTo(6f)
            arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
            verticalLineTo(20f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
            horizontalLineTo(18f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
            verticalLineTo(8f)
            close()
        }
        line {
            moveTo(14f, 2f)
            verticalLineTo(8f)
            horizontalLineTo(20f)
        }
        line {
            moveTo(8f, 13f)
            horizontalLineTo(16f)
            moveTo(8f, 17f)
            horizontalLineTo(13f)
        }
    }

    /** Remove / close: cross. */
    val Schliessen: ImageVector = icon("Schliessen") {
        line {
            moveTo(18f, 6f)
            lineTo(6f, 18f)
            moveTo(6f, 6f)
            lineTo(18f, 18f)
        }
    }

    /** Reply: arrow bending back to the left (ADR 0092). */
    val Antworten: ImageVector = icon("Antworten") {
        line {
            moveTo(9f, 7f)
            lineTo(4f, 12f)
            lineTo(9f, 17f)
        }
        line {
            moveTo(4f, 12f)
            horizontalLineTo(14f)
            curveTo(17.3f, 12f, 20f, 14.7f, 20f, 18f)
        }
    }

    /** Back: arrow to the left. */
    val Zurueck: ImageVector = icon("Zurueck") {
        line {
            moveTo(19f, 12f)
            horizontalLineTo(5f)
        }
        line {
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
    }

    /** Share: three connected dots. */
    val Teilen: ImageVector = icon("Teilen") {
        line {
            moveTo(8.2f, 10.8f)
            lineTo(15.8f, 6.7f)
        }
        line {
            moveTo(8.2f, 13.2f)
            lineTo(15.8f, 17.3f)
        }
        dot(18f, 5.5f, 2.6f)
        dot(6f, 12f, 2.6f)
        dot(18f, 18.5f, 2.6f)
    }

    /** Details of a message: small "i" in a circle. */
    val Info: ImageVector = icon("Info") {
        line {
            moveTo(21f, 12f)
            arcToRelative(9f, 9f, 0f, true, true, -18f, 0f)
            arcToRelative(9f, 9f, 0f, true, true, 18f, 0f)
        }
        line {
            moveTo(12f, 11f)
            verticalLineTo(16.5f)
        }
        dot(12f, 7.8f, 1.2f)
    }

    /** Being sent: clock. */
    val Uhr: ImageVector = icon("Uhr") {
        line {
            moveTo(20f, 12f)
            arcToRelative(8f, 8f, 0f, true, true, -16f, 0f)
            arcToRelative(8f, 8f, 0f, true, true, 16f, 0f)
        }
        line {
            moveTo(12f, 8f)
            verticalLineTo(12f)
            lineTo(15f, 14f)
        }
    }

    /** „Nicht gesendet“: circle with exclamation mark. */
    val Achtung: ImageVector = icon("Achtung") {
        line {
            moveTo(21f, 12f)
            arcToRelative(9f, 9f, 0f, true, true, -18f, 0f)
            arcToRelative(9f, 9f, 0f, true, true, 18f, 0f)
        }
        line {
            moveTo(12f, 7.5f)
            verticalLineTo(13f)
        }
        dot(12f, 16.4f, 1.2f)
    }

    /** Sent: one check mark. */
    val Haken: ImageVector = icon("Haken") {
        line {
            moveTo(5f, 12.5f)
            lineTo(9.5f, 17f)
            lineTo(19f, 7f)
        }
    }

    /** Arrived on the device: two check marks. Does not mean "read". */
    val DoppelHaken: ImageVector = icon("DoppelHaken") {
        line {
            moveTo(2f, 12.5f)
            lineTo(6.5f, 17f)
            lineTo(16f, 7f)
        }
        line {
            moveTo(11.5f, 16f)
            lineTo(12.5f, 17f)
            lineTo(22f, 7f)
        }
    }

    /** Save on the phone: arrow into a tray. */
    val Speichern: ImageVector = icon("Speichern") {
        line {
            moveTo(12f, 4f); verticalLineTo(15f)
            moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
            moveTo(4f, 15f); verticalLineTo(19f); horizontalLineTo(20f); verticalLineTo(15f)
        }
    }

    /** Path internet: globe. */
    val Internet: ImageVector = icon("Internet") {
        line {
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, true, false, 18f, 0f)
            arcToRelative(9f, 9f, 0f, true, false, -18f, 0f)
        }
        line {
            moveTo(3.5f, 9f); horizontalLineTo(20.5f)
            moveTo(3.5f, 15f); horizontalLineTo(20.5f)
        }
        line {
            moveTo(12f, 3f)
            curveToRelative(-4.5f, 4.8f, -4.5f, 13.2f, 0f, 18f)
            moveTo(12f, 3f)
            curveToRelative(4.5f, 4.8f, 4.5f, 13.2f, 0f, 18f)
        }
    }

    /** Path radio: antenna with waves. */
    val Funk: ImageVector = icon("Funk") {
        dot(12f, 10f, 1.8f)
        line {
            moveTo(12f, 12f)
            lineTo(12f, 21f)
        }
        line {
            moveTo(8.5f, 6.5f)
            arcToRelative(5f, 5f, 0f, false, false, 0f, 7f)
            moveTo(15.5f, 6.5f)
            arcToRelative(5f, 5f, 0f, false, true, 0f, 7f)
        }
        line {
            moveTo(5.6f, 3.6f)
            arcToRelative(9f, 9f, 0f, false, false, 0f, 12.8f)
            moveTo(18.4f, 3.6f)
            arcToRelative(9f, 9f, 0f, false, true, 0f, 12.8f)
        }
    }

    /** Messages: speech bubble with three dots. */
    val Nachrichten: ImageVector = icon("Nachrichten") {
        line {
            moveTo(5f, 4.5f)
            horizontalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(15f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(10f)
            lineTo(6f, 20.5f)
            verticalLineTo(17f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(6.5f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
        }
        dot(8f, 10.75f, 1.2f)
        dot(12f, 10.75f, 1.2f)
        dot(16f, 10.75f, 1.2f)
    }

    /** Settings: gear. */
    /** More: three dots, same weight as the dots in „Kreis“. */
    val Mehr: ImageVector = icon("Mehr") {
        dot(5f, 12f, 2.2f)
        dot(12f, 12f, 2.2f)
        dot(19f, 12f, 2.2f)
    }

    /** Menu in the header of every tab (ADR 0062 §1): three dots one below the other. */
    val Menue: ImageVector = icon("Menue") {
        dot(12f, 5f, 2f)
        dot(12f, 12f, 2f)
        dot(12f, 19f, 2f)
    }

    /** Alerts: bell. */
    val Glocke: ImageVector = icon("Glocke") {
        line {
            moveTo(6f, 16.5f)
            verticalLineTo(11f)
            arcToRelative(6f, 6f, 0f, false, true, 12f, 0f)
            verticalLineToRelative(5.5f)
            lineToRelative(1.5f, 1.8f)
            horizontalLineTo(4.5f)
            close()
        }
        line {
            moveTo(10f, 20.8f)
            arcToRelative(2.2f, 2.2f, 0f, false, false, 4f, 0f)
        }
        line {
            moveTo(12f, 3f)
            verticalLineTo(5f)
        }
    }

    val Einstellungen: ImageVector = icon("Einstellungen") {
        line {
            moveTo(9f, 12f)
            arcToRelative(3f, 3f, 0f, true, false, 6f, 0f)
            arcToRelative(3f, 3f, 0f, true, false, -6f, 0f)
        }
        line {
            moveTo(12f, 3f); verticalLineTo(5.5f)
            moveTo(12f, 18.5f); verticalLineTo(21f)
            moveTo(3f, 12f); horizontalLineTo(5.5f)
            moveTo(18.5f, 12f); horizontalLineTo(21f)
            moveTo(5.6f, 5.6f); lineTo(7.4f, 7.4f)
            moveTo(16.6f, 16.6f); lineTo(18.4f, 18.4f)
            moveTo(5.6f, 18.4f); lineTo(7.4f, 16.6f)
            moveTo(16.6f, 7.4f); lineTo(18.4f, 5.6f)
        }
        line {
            moveTo(6.5f, 12f)
            arcToRelative(5.5f, 5.5f, 0f, true, false, 11f, 0f)
            arcToRelative(5.5f, 5.5f, 0f, true, false, -11f, 0f)
        }
    }

    // Rounded rectangle as a line, for the icons at the bottom.
    private fun androidx.compose.ui.graphics.vector.PathBuilder.box(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        horizontalLineTo(x + w - r)
        arcToRelative(r, r, 0f, false, true, r, r)
        verticalLineTo(y + h - r)
        arcToRelative(r, r, 0f, false, true, -r, r)
        horizontalLineTo(x + r)
        arcToRelative(r, r, 0f, false, true, -r, -r)
        verticalLineTo(y + r)
        arcToRelative(r, r, 0f, false, true, r, -r)
        close()
    }

    /** Add: plus. */
    val Plus: ImageVector = icon("Plus") {
        line {
            moveTo(12f, 5f); verticalLineTo(19f)
            moveTo(5f, 12f); horizontalLineTo(19f)
        }
    }

    /** Search: magnifying glass. */
    val Suche: ImageVector = icon("Suche") {
        line {
            moveTo(17f, 10.5f)
            arcToRelative(6.5f, 6.5f, 0f, true, true, -13f, 0f)
            arcToRelative(6.5f, 6.5f, 0f, true, true, 13f, 0f)
        }
        line {
            moveTo(15.2f, 15.2f)
            lineTo(20f, 20f)
        }
    }

    /** On to a subpage: arrowhead to the right. */
    val Weiter: ImageVector = icon("Weiter") {
        line {
            moveTo(9f, 6f)
            lineTo(15f, 12f)
            lineTo(9f, 18f)
        }
    }

    /** Edit: pencil. */
    val Stift: ImageVector = icon("Stift") {
        line {
            moveTo(4f, 20f)
            lineTo(8f, 20f)
            lineTo(18.5f, 9.5f)
            lineTo(14.5f, 5.5f)
            lineTo(4f, 16f)
            close()
        }
        line {
            moveTo(12.5f, 7.5f)
            lineTo(16.5f, 11.5f)
        }
    }

    /** Invitation or contact link: two chain links. */
    val Link: ImageVector = icon("Link") {
        line {
            moveTo(10f, 13f)
            arcToRelative(5f, 5f, 0f, false, false, 7.5f, 0.5f)
            lineToRelative(3f, -3f)
            arcToRelative(5f, 5f, 0f, false, false, -7.1f, -7.1f)
            lineToRelative(-1.7f, 1.7f)
        }
        line {
            moveTo(14f, 11f)
            arcToRelative(5f, 5f, 0f, false, false, -7.5f, -0.5f)
            lineToRelative(-3f, 3f)
            arcToRelative(5f, 5f, 0f, false, false, 7.1f, 7.1f)
            lineToRelative(1.7f, -1.7f)
        }
    }

    /** QR code: three corners and dots. */
    val QrCode: ImageVector = icon("QrCode") {
        line {
            box(3f, 3f, 7f, 7f, 1.5f)
            box(14f, 3f, 7f, 7f, 1.5f)
            box(3f, 14f, 7f, 7f, 1.5f)
        }
        dot(15f, 15f, 1.2f)
        dot(20f, 15f, 1.2f)
        dot(17.5f, 17.5f, 1.2f)
        dot(15f, 20f, 1.2f)
        dot(20f, 20f, 1.2f)
    }

    /** Copy: two sheets on top of each other. */
    val Kopieren: ImageVector = icon("Kopieren") {
        line { box(9f, 9f, 12f, 12f, 2f) }
        line {
            moveTo(5f, 15f)
            horizontalLineTo(4.5f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
            verticalLineTo(4.5f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
            horizontalLineTo(13.5f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
            verticalLineTo(5f)
        }
    }

    /** Battery of the radio. */
    val Akku: ImageVector = icon("Akku") {
        line { box(2f, 7f, 18f, 10f, 2f) }
        line {
            moveTo(22.5f, 10.5f); verticalLineTo(13.5f)
            moveTo(6f, 10.5f); verticalLineTo(13.5f)
            moveTo(10f, 10.5f); verticalLineTo(13.5f)
        }
    }

    /** Refresh: arrow in a circle. */
    val Aktualisieren: ImageVector = icon("Aktualisieren") {
        line {
            moveTo(21f, 4f)
            verticalLineTo(9f)
            horizontalLineTo(16f)
        }
        line {
            moveTo(20f, 13.5f)
            arcToRelative(8f, 8f, 0f, true, true, -2.3f, -7.2f)
            lineTo(21f, 9f)
        }
    }

    /** Leave: door with arrow pointing out. */
    val Verlassen: ImageVector = icon("Verlassen") {
        line {
            moveTo(9f, 21f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            horizontalLineTo(9f)
        }
        line {
            moveTo(16f, 17f)
            lineTo(21f, 12f)
            lineTo(16f, 7f)
            moveTo(21f, 12f)
            horizontalLineTo(9f)
        }
    }

    /** Take or choose a photo: camera. */
    val Kamera: ImageVector = icon("Kamera") {
        line {
            moveTo(4f, 7f)
            horizontalLineTo(7f)
            lineTo(9f, 4.5f)
            horizontalLineTo(15f)
            lineTo(17f, 7f)
            horizontalLineTo(20f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(18f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(4f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(9f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
        }
        line {
            moveTo(8.5f, 13f)
            arcToRelative(3.5f, 3.5f, 0f, true, false, 7f, 0f)
            arcToRelative(3.5f, 3.5f, 0f, true, false, -7f, 0f)
        }
    }

    /** Delete: bin (as in the web app). */
    val Papierkorb: ImageVector = icon("Papierkorb") {
        line {
            moveTo(3f, 6f)
            horizontalLineTo(21f)
            moveTo(8f, 6f)
            verticalLineTo(4f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            horizontalLineTo(14f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(6f)
            moveTo(19f, 6f)
            lineTo(18f, 20f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(8f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            lineTo(5f, 6f)
        }
    }

    /** Advanced: two sliders. */
    val Regler: ImageVector = icon("Regler") {
        line {
            moveTo(4f, 7f); horizontalLineTo(11.5f)
            moveTo(16.5f, 7f); horizontalLineTo(20f)
            moveTo(4f, 17f); horizontalLineTo(7.5f)
            moveTo(12.5f, 17f); horizontalLineTo(20f)
        }
        line {
            moveTo(11.5f, 7f)
            arcToRelative(2.5f, 2.5f, 0f, true, false, 5f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, true, false, -5f, 0f)
            moveTo(7.5f, 17f)
            arcToRelative(2.5f, 2.5f, 0f, true, false, 5f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, true, false, -5f, 0f)
        }
    }

    /** Troubleshooting: wrench, jaw open at the top right. */
    val Werkzeug: ImageVector = icon("Werkzeug") {
        line {
            moveTo(17.54f, 3.77f)
            arcTo(4.5f, 4.5f, 0f, true, false, 20.23f, 6.46f)
            lineTo(17.4f, 6.6f)
            close()
        }
        line {
            moveTo(12.82f, 11.18f)
            lineTo(4.5f, 19.5f)
        }
    }

    /** Passwords: key with a round bow. */
    val Schluessel: ImageVector = icon("Schluessel") {
        line {
            moveTo(12.5f, 15f)
            arcToRelative(4.5f, 4.5f, 0f, true, true, -9f, 0f)
            arcToRelative(4.5f, 4.5f, 0f, true, true, 9f, 0f)
        }
        line {
            moveTo(11.2f, 11.8f)
            lineTo(20f, 3f)
            moveTo(16.5f, 6.5f)
            lineTo(19f, 9f)
            moveTo(14f, 9f)
            lineTo(16f, 11f)
        }
    }

    /** Lock (old invitation links): padlock. */
    val Schloss: ImageVector = icon("Schloss") {
        line { box(5f, 11f, 14f, 10f, 2f) }
        line {
            moveTo(8f, 11f)
            verticalLineTo(7f)
            arcToRelative(4f, 4f, 0f, false, true, 8f, 0f)
            verticalLineTo(11f)
        }
    }
}
