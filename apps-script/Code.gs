/**
 * FitPlan MASTER API v2
 * Rebuilt from the current FitPlan frontend contract.
 * Spreadsheet: FitPlan_MASTER
 */
const FP_SPREADSHEET_ID = "1UZJUXKXz7KmX-i1d72sy0L19RXe-gxj49ZefWjHt0kE";
const FP_APP = "FitPlan - Plani im";
const FP_VERSION = "2.0.0";

function FP_SS_(){ return SpreadsheetApp.openById(FP_SPREADSHEET_ID); }
function FP_JSON_(o){ return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }
function FP_OK_(o){ return FP_JSON_(Object.assign({ok:true},o||{})); }
function FP_ERR_(e){ return FP_JSON_({ok:false,error:String(e && e.message ? e.message : e)}); }
function FP_NUM_(v){
  if(typeof v==="number") return v;
  if(v===null||v===undefined||v==="") return 0;
  var n=Number(String(v).replace(/\s/g,"").replace(",","."));
  return isFinite(n)?n:0;
}
function FP_ROUND_(v){ return Math.round((FP_NUM_(v)+Number.EPSILON)*10)/10; }
function FP_ID_(d){ return String((d||{}).profile_id||"").trim(); }
function FP_NOW_(){ return new Date(); }

function doGet(e){
  try{
    var a=e&&e.parameter&&e.parameter.action;
    if(a==="profile"||a==="getProfile") return FP_OK_({profile:FP_GET_PROFILE_((e.parameter||{}).profile_id)});
    if(a==="dashboard") return FP_OK_({dashboard:FP_DASHBOARD_((e.parameter||{}).profile_id)});
    return FP_OK_({app:FP_APP,version:FP_VERSION,status:"online",time:new Date().toISOString()});
  }catch(err){ return FP_ERR_(err); }
}

function doPost(e){
  try{
    var d={};
    if(e&&e.postData&&e.postData.contents) d=JSON.parse(e.postData.contents);
    var a=String(d.action||"").trim();
    if(!a) throw new Error("Mungon action");

    switch(a){
      case "register":
      case "saveProfile": return FP_OK_({profile:FP_SAVE_PROFILE_(d)});
      case "getProfile": return FP_OK_({profile:FP_GET_PROFILE_(FP_ID_(d))});
      case "saveMeasurement": return FP_OK_({saved:FP_APPEND_("Measurements",d)});
      case "saveBMI": return FP_OK_({saved:FP_APPEND_("BMI_Calories",d)});
      case "saveDiet": return FP_OK_({saved:FP_APPEND_("Diet",d)});
      case "saveAlmased": return FP_OK_({saved:FP_APPEND_("Almased",d)});
      case "saveExercise": return FP_OK_({saved:FP_APPEND_("Exercises",d)});
      case "saveProgress": return FP_OK_({saved:FP_APPEND_("Progress",d)});
      case "saveSteps": return FP_OK_({saved:FP_APPEND_("Steps",d)});
      case "saveWater": return FP_OK_({saved:FP_APPEND_("Water",d)});
      case "saveTreadmill": return FP_OK_({saved:FP_APPEND_("Treadmill",d)});
      case "saveReminder": return FP_OK_({saved:FP_APPEND_("Reminders",d)});
      case "saveDailySummary": return FP_OK_({saved:FP_APPEND_("Daily_Summary",d)});
      case "dashboard": return FP_OK_({dashboard:FP_DASHBOARD_(FP_ID_(d))});

      // Real diet engine
      case "getDietProducts": return FP_getDietProducts();
      case "calculateProduct": return FP_calculateProduct(d.product_id||d.id,d.amount);
      case "calculateMeal": return FP_calculateMeal(d.items||[]);
      case "almased": return FP_almased(d.height_cm,d.grams,d.with_oil);

      // AI
      case "ai": return FP_AI_(d);
      case "aiMeal": return FP_AI_MEAL_(d);
      default: throw new Error("Action i panjohur: "+a);
    }
  }catch(err){ return FP_ERR_(err); }
}

function FP_SHEET_(name){
  var sh=FP_SS_().getSheetByName(name);
  if(!sh) throw new Error("Nuk u gjet sheet-i: "+name);
  return sh;
}
function FP_HEADERS_(sh){
  var last=Math.max(1,sh.getLastColumn());
  return sh.getRange(1,1,1,last).getDisplayValues()[0].map(function(x){return String(x).trim();});
}
function FP_APPEND_(sheetName,d){
  var sh=FP_SHEET_(sheetName), h=FP_HEADERS_(sh);
  var aliases={
    "Profile ID":"profile_id","profile_id":"profile_id","ID":"profile_id",
    "Timestamp":"timestamp","Data/Ora":"timestamp","Date":"date","Data":"date"
  };
  var row=h.map(function(k){
    var key=aliases[k]||k;
    if(key==="timestamp") return FP_NOW_();
    if(Object.prototype.hasOwnProperty.call(d,key)) return d[key];
    var low=String(key).toLowerCase().replace(/\s+/g,"_");
    if(Object.prototype.hasOwnProperty.call(d,low)) return d[low];
    return "";
  });
  sh.appendRow(row);
  return {sheet:sheetName,row:sh.getLastRow()};
}

function FP_SAVE_PROFILE_(d){
  var sh=FP_SHEET_("Profiles"), h=FP_HEADERS_(sh), pid=FP_ID_(d);
  if(!pid) throw new Error("Mungon profile_id");
  var idCol=h.findIndex(function(x){return /^(profile[_ ]?id|id)$/i.test(x);});
  if(idCol<0) idCol=0;
  var rowNum=0,last=sh.getLastRow();
  if(last>1){
    var vals=sh.getRange(2,idCol+1,last-1,1).getDisplayValues();
    for(var i=0;i<vals.length;i++) if(String(vals[i][0])===pid){rowNum=i+2;break;}
  }
  var aliases={
    "profile_id":"profile_id","id":"profile_id","timestamp":"timestamp",
    "emri":"emri","mbiemri":"mbiemri","datelindja":"datelindja","mosha":"mosha",
    "gjinia":"gjinia","email":"email","telefon":"telefon","gjatesia_cm":"gjatesia_cm",
    "pesha_aktuale_kg":"pesha_aktuale_kg","pesha_synim_kg":"pesha_synim_kg",
    "niveli_aktivitetit":"niveli_aktivitetit","qellimi":"qellimi","diet_mode":"diet_mode",
    "almased_phase":"almased_phase","exercise_level":"exercise_level","exercise_place":"exercise_place",
    "daily_step_goal":"daily_step_goal","daily_water_ml":"daily_water_ml","language":"language","device_id":"device_id"
  };
  var row=h.map(function(label){
    var k=String(label).trim().toLowerCase().replace(/[^a-z0-9_]+/g,"_").replace(/^_|_$/g,"");
    if(/^(profile_?id|id)$/.test(k)) return pid;
    if(/timestamp|created|updated/.test(k)) return FP_NOW_();
    var key=aliases[k]||k;
    return Object.prototype.hasOwnProperty.call(d,key)?d[key]:"";
  });
  if(rowNum) sh.getRange(rowNum,1,1,row.length).setValues([row]);
  else {sh.appendRow(row);rowNum=sh.getLastRow();}
  return Object.assign({profile_id:pid,row:rowNum},d);
}

function FP_GET_PROFILE_(pid){
  pid=String(pid||"").trim(); if(!pid) return {};
  var sh=FP_SHEET_("Profiles"),h=FP_HEADERS_(sh),last=sh.getLastRow();
  if(last<2)return {};
  var data=sh.getRange(2,1,last-1,h.length).getValues();
  var idCol=h.findIndex(function(x){return /^(profile[_ ]?id|id)$/i.test(String(x));}); if(idCol<0)idCol=0;
  for(var i=data.length-1;i>=0;i--){
    if(String(data[i][idCol])===pid){
      var o={};h.forEach(function(k,j){o[String(k).trim()]=data[i][j];});
      // normalized keys expected by frontend
      o.profile_id=pid;
      return o;
    }
  }
  return {};
}

function FP_DASHBOARD_(pid){
  return {profile:FP_GET_PROFILE_(pid),generated_at:new Date().toISOString()};
}

/* ---------------- DIET PRODUCTS / PRECISE CALORIES ---------------- */
function FP_DIET_SHEET_(){
  var sh=FP_SS_().getSheetByName("Diet_Products");
  if(!sh) throw new Error("Nuk u gjet Diet_Products");
  return sh;
}
function FP_getDietProducts(){
  var sh=FP_DIET_SHEET_(),last=sh.getLastRow();
  if(last<4) return {ok:true,products:[]};
  var rows=sh.getRange(4,1,last-3,16).getValues(), products=[];
  rows.forEach(function(r){
    if(!String(r[0]||"").trim()) return;
    products.push({
      id:String(r[0]).trim(),name:r[1],unit:r[2],
      kcal100:FP_NUM_(r[3]),protein100:FP_NUM_(r[4]),carbs100:FP_NUM_(r[5]),fat100:FP_NUM_(r[6]),
      defaultAmount:FP_NUM_(r[7]),image:r[12]||"",source:r[13]||"",verified:r[14]||"",note:r[15]||""
    });
  });
  return {ok:true,products:products};
}
function FP_PRODUCT_(id){
  var needle=String(id||"").trim().toLowerCase(), p=FP_getDietProducts().products;
  for(var i=0;i<p.length;i++) if(String(p[i].id).toLowerCase()===needle)return p[i];
  throw new Error("Produkti nuk u gjet: "+id);
}
function FP_CALC100_(amount,value100){return FP_ROUND_(FP_NUM_(amount)*FP_NUM_(value100)/100);}
function FP_calculateProduct(id,amount){
  var p=FP_PRODUCT_(id),a=FP_NUM_(amount);
  if(!a) a=p.defaultAmount;
  return {ok:true,product:{
    id:p.id,name:p.name,amount:a,unit:p.unit,
    kcal:FP_CALC100_(a,p.kcal100),protein:FP_CALC100_(a,p.protein100),
    carbs:FP_CALC100_(a,p.carbs100),fat:FP_CALC100_(a,p.fat100),
    image:p.image,source:p.source,verified:p.verified
  }};
}
function FP_calculateMeal(items){
  if(!Array.isArray(items)) throw new Error("items duhet të jetë listë");
  var out={kcal:0,protein:0,carbs:0,fat:0,items:[]};
  items.forEach(function(it){
    var x=FP_calculateProduct(it.product_id||it.id,it.amount).product;
    out.items.push(x);out.kcal+=x.kcal;out.protein+=x.protein;out.carbs+=x.carbs;out.fat+=x.fat;
  });
  out.kcal=FP_ROUND_(out.kcal);out.protein=FP_ROUND_(out.protein);out.carbs=FP_ROUND_(out.carbs);out.fat=FP_ROUND_(out.fat);
  return {ok:true,meal:out};
}
function FP_almased(heightCm,grams,withOil){
  var h=FP_NUM_(heightCm);
  if(!grams){
    if(h>=200)grams=100; else if(h>=190)grams=90; else if(h>=180)grams=80;
    else if(h>=170)grams=70; else if(h>=160)grams=60; else grams=50;
  }
  grams=FP_NUM_(grams);
  var items=[{product_id:"ALM-ORG",amount:grams},{product_id:"WATER",amount:200}];
  if(withOil!==false)items.push({product_id:"RAPS-01",amount:6});
  var calc=FP_calculateMeal(items).meal;
  return {ok:true,type:"almased",almasedGrams:grams,waterMl:200,oilGrams:withOil===false?0:6,
    kcal:calc.kcal,protein:calc.protein,carbs:calc.carbs,fat:calc.fat,items:calc.items};
}
function FP_TEST_DIET(){
  Logger.log(JSON.stringify({products:FP_getDietProducts(),almased:FP_almased(150,50,true)},null,2));
}

/* ---------------- AI ---------------- */
function FP_OPENAI_(payload){
  var props=PropertiesService.getScriptProperties();
  var key=props.getProperty("OPENAI_API_KEY");
  if(!key) throw new Error("OPENAI_API_KEY mungon në Script Properties");
  var model=props.getProperty("OPENAI_MODEL")||"gpt-5-mini";
  var r=UrlFetchApp.fetch("https://api.openai.com/v1/responses",{
    method:"post",contentType:"application/json",
    headers:{Authorization:"Bearer "+key},
    payload:JSON.stringify(Object.assign({model:model},payload)),
    muteHttpExceptions:true
  });
  var status=r.getResponseCode(),j=JSON.parse(r.getContentText()||"{}");
  if(status<200||status>=300) throw new Error((j.error&&j.error.message)||("OpenAI HTTP "+status));
  var answer=j.output_text||"";
  if(!answer&&j.output){
    j.output.forEach(function(o){(o.content||[]).forEach(function(c){if(c.text)answer+=c.text;});});
  }
  return answer;
}
function FP_AI_(d){
  var p=FP_GET_PROFILE_(FP_ID_(d));
  var q=String(d.question||"").trim();if(!q)throw new Error("Mungon pyetja");
  var answer=FP_OPENAI_({input:"Ti je trajneri FitPlan. Përgjigju shqip, qartë dhe praktikisht. Mos shpik të dhëna mjekësore. Profili: "+JSON.stringify(p)+"\nPyetja: "+q});
  try{FP_APPEND_("AI_History",{profile_id:FP_ID_(d),question:q,answer:answer,timestamp:FP_NOW_()});}catch(_){}
  return FP_OK_({answer:answer});
}
function FP_AI_MEAL_(d){
  var img=String(d.image_data||"");if(!img)throw new Error("Mungon image_data");
  var prompt=String(d.prompt||"Analizo pjatën dhe vlerëso kaloritë e makrot.");
  var answer=FP_OPENAI_({input:[{role:"user",content:[{type:"input_text",text:prompt},{type:"input_image",image_url:img}]}]});
  try{FP_APPEND_("AI_History",{profile_id:FP_ID_(d),question:"AI meal photo",answer:answer,timestamp:FP_NOW_()});}catch(_){}
  return FP_OK_({answer:answer});
}
