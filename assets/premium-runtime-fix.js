(()=>{"use strict";
// Compatibility layer for Premium dynamic UI. i18n.js is loaded before this file.
window.TT=window.TT||function(text){
  return typeof window.FitPlanTranslate==="function"?window.FitPlanTranslate(String(text??"")):String(text??"");
};
// Re-translate dynamic Premium output after language resources are ready.
function refreshPremiumI18n(){
  if(window.FitPlanI18n&&typeof window.FitPlanI18n.apply==="function") window.FitPlanI18n.apply();
}
if(document.readyState==="loading") document.addEventListener("DOMContentLoaded",refreshPremiumI18n,{once:true});
else refreshPremiumI18n();
})();