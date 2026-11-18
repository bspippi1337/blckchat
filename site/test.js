"use strict";
/* Field-test checklist. No secrets, messages, tokens or phone numbers.
 * A small browser-only convenience; no network calls and no analytics. */
(() => {
  const storageKey="blckchat42-field-test-v1";
  const controls=[...document.querySelectorAll("input[data-test-id]")];
  const progress=document.getElementById("test-progress");
  const fill=document.getElementById("test-progress-fill");
  const completed=document.getElementById("completed-count");
  const total=document.getElementById("total-count");
  const summary=document.getElementById("progress-summary");
  const copy=document.getElementById("copy-report");
  const reset=document.getElementById("reset-tests");
  const status=document.getElementById("test-report-status");
  const fallback=document.getElementById("test-report-fallback");
  if(!controls.length || !progress || !fill || !copy || !reset)return;
  const allowed=new Set(controls.map(input=>input.dataset.testId));
  const readStored=()=>{
    try{
      const saved=JSON.parse(localStorage.getItem(storageKey)||"[]");
      if(!Array.isArray(saved))return [];
      return saved.filter(id=>typeof id==="string" && allowed.has(id));
    }catch(_){return [];}
  };
  const checked=new Set(readStored());
  controls.forEach(input=>{input.checked=checked.has(input.dataset.testId);});
  const persist=()=>{
    try{
      localStorage.setItem(storageKey,
        JSON.stringify(controls.filter(input=>input.checked).map(input=>input.dataset.testId)));
    }catch(_){}
  };
  const update=()=>{
    const done=controls.filter(input=>input.checked).length;
    completed.textContent=String(done);
    total.textContent=String(controls.length);
    progress.setAttribute("aria-valuemax",String(controls.length));
    progress.setAttribute("aria-valuenow",String(done));
    fill.style.width=(100*done/controls.length)+"%";
    summary.textContent=done===controls.length
      ?"Alle punktene er avkrysset. Del rapporten for gjennomgang, ikke som bevis for en sikkerhetsrevisjon."
      :done===0?"Klar til å starte testen. Kryss bare av for handlinger dere faktisk har gjennomført."
      :done+" av "+controls.length+" gjennomført. Neste steg står nedenfor.";
  };
  for(const input of controls){
    input.addEventListener("change",()=>{
      persist();
      update();
      status.textContent="";
    });
  }
  const report=()=>{
    const lines=[
      "BLCKCHAT 42 / FELTTEST",
      "Klient: Android SMS Alpha 0.4 (bekreft installert versjon separat)",
      "Generert: "+new Date().toISOString(),
      "Roller: Phone A (Admin) / Phone B",
      "Gjennomført: "+controls.filter(i=>i.checked).length+"/"+controls.length,
      "Ingen telefonnummer, nøkler eller meldinger inkludert.",
      ""
    ];
    for(const input of controls){
      const heading=input.closest("label")?.querySelector("strong")?.textContent?.trim()
        || input.dataset.testId;
      lines.push((input.checked?"[OK] ":"[ ] ")+heading);
    }
    lines.push("MERK: Manuelle avkrysninger er ikke en uavhengig sikkerhetsrevisjon.");
    return lines.join("\n");
  };
  copy.addEventListener("click",async()=>{
    const text=report();
    try{
      if(!navigator.clipboard?.writeText)
        throw new Error("No clipboard permission");
      await navigator.clipboard.writeText(text);
      fallback.hidden=true;
      status.textContent="Anonym testrapport kopiert. Du kan lime den inn i en melding.";
    }catch(_){
      fallback.value=text;
      fallback.hidden=false;
      fallback.focus();
      fallback.select();
      status.textContent="Rapporten står i feltet under. Marker og kopier den manuelt.";
    }
  });
  reset.addEventListener("click",()=>{
    if(!window.confirm("Nullstille alle lokale avkrysninger? Ingen meldinger eller appdata påvirkes."))
      return;
    controls.forEach(input=>{input.checked=false;});
    persist();
    update();
    fallback.hidden=true;
    status.textContent="Alle lokale kontrollpunkter er nullstilt.";
  });
  update();
})();