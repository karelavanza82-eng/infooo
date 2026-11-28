# Hoisly Control Center – first repair pass

Changes in this archive:
- Landscape Control Center now uses a dedicated compact horizontal composition instead of reusing the portrait absolute-position layout.
- Landscape reference scaling is based on the 568x360 landscape canvas, preventing the previous width-driven oversizing.
- Carrier, signal and battery information were removed from the Control Center content; the dedicated Hoisly status-bar overlay remains responsible for status indicators.
- Music Player inner artwork placeholder, AirPlay/output button and transport controls now use the shared Hoisly Liquid Glass surface instead of flat translucent backgrounds.

Important: this is a first source patch, not a verified release build. The Gradle wrapper could not download Gradle in this environment (network unavailable), so compilation and tests have not been run.

Still pending from the full request:
- Landscape two-swipe sequence (first status bar only, second Control Center).
- Per-control resize handles and freely configurable geometry for every tile/slider.
- Automatic device-specific native quick-setting discovery. Android does not expose a universal API to clone OEM SystemUI tiles; the safe implementation must use supported Android tile APIs and capability-aware Hoisly shortcuts.
