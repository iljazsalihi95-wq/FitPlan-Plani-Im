(()=>{"use strict";
window.TT=window.TT||function(text){return typeof window.FitPlanTranslate==="function"?window.FitPlanTranslate(String(text??"")):String(text??"")};
function refreshPremiumI18n(){if(window.FitPlanI18n&&typeof window.FitPlanI18n.apply==="function")window.FitPlanI18n.apply()}
function cleanBrokenAI(){document.querySelectorAll('button,label,a,div').forEach(el=>{const t=(el.textContent||'').trim().toLowerCase();if((t==='ai load files'||t==='load files'||t==='ngarko fajlet'||t==='ngarko skedarët')&&!el.querySelector('input[type=file]'))el.style.display='none'});document.querySelectorAll('img').forEach(img=>{img.referrerPolicy='no-referrer';img.addEventListener('error',()=>{if(img.dataset.fpFallback)return;img.dataset.fpFallback='1';if(/exercise|workout|ushtrim/i.test(img.alt||''))img.src='images/ushtrimet.png'}, {once:true})})}
function refresh(){refreshPremiumI18n();cleanBrokenAI()}
if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",refresh,{once:true});else refresh();
new MutationObserver(refresh).observe(document.documentElement,{childList:true,subtree:true});
})();