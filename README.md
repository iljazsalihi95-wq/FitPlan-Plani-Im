# FitPlan – Plani im
PWA/mobile web app with 13 integrated modules:
Ballina, Profili, BMI & Analiza, Deficiti Kalorik, Dieta 7-ditore, Dieta me Almased (Faza 2), Ushtrimet, Progresi, Uji & Hapat, Cikli Menstrual, Lista e Blerjeve, Trajneri AI, Kalendari & Kujtesat.

## Architecture
- Single app navigation with Back/Home
- Shared profile in localStorage with legacy-key migration
- BMI updates no longer replace the whole profile
- Daily water/steps history
- Cycle restore
- Almased Phase 2 daily tracker
- PWA manifest + offline service worker

## Almased Phase 2
The module follows the official Phase 2 structure: two shakes plus one balanced solid meal, preferably the solid meal at lunch. Portion amount is based on height and should always be checked against the current product label/official instructions.
