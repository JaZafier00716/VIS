// node render.js <dir> : renders every ui-*.html to ../ui-*.png (2x)
const {chromium}=require('playwright');const fs=require('fs');const path=require('path');
(async()=>{const dir=process.argv[2];const out=process.argv[3];const b=await chromium.launch();
const p=await b.newPage({deviceScaleFactor:2,viewport:{width:1400,height:900}});
for(const f of fs.readdirSync(dir).filter(f=>/^ui-.*\.html$/.test(f)&&(!process.argv[4]||f.includes(process.argv[4])))){
 await p.goto('file://'+path.join(dir,f));await p.evaluate(()=>document.fonts.ready);
 const el=await p.$('body');const bb=await p.evaluate(()=>{const b=document.body;return{w:b.scrollWidth,h:b.scrollHeight}});
 await p.setViewportSize({width:Math.ceil(bb.w),height:Math.ceil(bb.h)});
 await p.screenshot({path:path.join(out,f.replace('.html','.png')),fullPage:true});console.log('ok',f);}
await b.close();})();
