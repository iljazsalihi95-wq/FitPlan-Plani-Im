(()=>{
'use strict';
const FALLBACK='images/ushtrimet.png';
function fixImages(root=document){
  root.querySelectorAll('img.fit-img').forEach(img=>{
    img.referrerPolicy='no-referrer';
    img.decoding='async';
    img.addEventListener('error',()=>{
      if(img.dataset.fallbackDone)return;
      img.dataset.fallbackDone='1';
      img.src=FALLBACK;
      img.alt=(img.alt||'Ushtrim')+' – pamje rezervë';
    },{once:true});
  });
}
function fixAI(root=document){
  root.querySelectorAll('button,label,a,div').forEach(el=>{
    const t=(el.textContent||'').trim().toLowerCase();
    if((t==='ai load files'||t==='load files'||t==='ngarko fajlet'||t==='ngarko skedarët') && !el.querySelector('#mealPhoto')) el.style.display='none';
  });
  const out=root.querySelector('#aiA');
  if(out){out.style.whiteSpace='pre-wrap';out.style.lineHeight='1.6';out.style.minHeight='72px';}
  const q=root.querySelector('#aiQ');
  if(q){q.placeholder='Pyet për dietën, gjizën, kaloritë, ushtrimet ose progresin tënd…';q.autocomplete='off';}
}
function polish(root=document){fixImages(root);fixAI(root)}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>polish(),{once:true});else polish();
new MutationObserver(()=>polish()).observe(document.documentElement,{childList:true,subtree:true});
window.addEventListener('error',e=>{const x=e.target;if(x&&x.tagName==='IMG'&&x.classList.contains('fit-img')){if(!x.dataset.fallbackDone){x.dataset.fallbackDone='1';x.src=FALLBACK}}},true);
})();