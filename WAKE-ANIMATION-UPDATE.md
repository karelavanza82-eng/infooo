# Wake / unlock animation (iOS-style)

Files: `ui/home/WakeAnimation.kt` (state, master clock, `wakePop`), `ui/home/WakeSpring.kt`
(spring math), `ui/home/ParallaxWallpaper.kt`, `ui/home/HomeScreen.kt`, `ui/home/HomeWidget.kt`,
`ui/theme/Motion.kt` (dead wake specs removed), test `WakeSpringTest.kt`.

Root cause of the blank home: while the display was off, `WakeState.armed` forced every icon and
widget to alpha 0 until `ACTION_USER_PRESENT`; the system unlock animation therefore revealed an
empty home. Each item then also waited for a `LaunchedEffect` + `delay`, adding more frames.

Now: nothing is hidden. Armed only holds a "closer" pose (icons 1.55x, widgets 1.35x, dock 1.2x,
scaled about the screen centre, wallpaper zoomed). One master clock replays on unlock and each
item evaluates its own spring (damping 0.68, stiffness 180) delayed by position (top-left first,
dock last, 170 ms spread). No per-item coroutine, no per-frame recomposition.

Limits: the system keyguard animation cannot be replaced by a launcher. Pop/Top/Side styles still
start from their own off-screen/small pose by design. Not built or run on a device yet.
