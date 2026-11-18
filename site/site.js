"use strict";

/* BLCKCHAT's living monogram, including an accessible no-motion reveal.
 * The branded effect is decorative: controls and downloads never depend on it. */
(() => {
  const mark=document.querySelector(".brand-motion");
  const replay=document.querySelector("[data-brand-replay]");
  if(!mark || !replay)return;
  const reduced=window.matchMedia("(prefers-reduced-motion: reduce)");
  const setLabel=()=>{
    const staticHat=mark.classList.contains("bm-static-blackhat");
    replay.lastChild.textContent=reduced.matches
      ?(staticHat?" SHOW BLCK/CHAT":" SHOW BLACK HAT")
      :" REPLAY THE TRANSFORMATION";
    replay.setAttribute("aria-label",reduced.matches
      ?(staticHat?"Show the original BLCKCHAT logo":"Show the BLACK HAT logo without animation")
      :"Replay the BLCKCHAT to BLACK HAT logo animation");
  };
  replay.addEventListener("click",()=>{
    if(reduced.matches){
      mark.classList.toggle("bm-static-blackhat");
    }else{
      mark.classList.remove("bm-static-blackhat");
      // Restart the actual CSS animations from their first frame.
      // Avoid repeated DOM cloning or artificial timeouts.
      for(const animation of mark.getAnimations({subtree:true})){
        animation.cancel();
        animation.play();
      }
    }
    setLabel();
  });
  if(typeof reduced.addEventListener==="function")
    reduced.addEventListener("change",()=>{
      mark.classList.remove("bm-static-blackhat");
      setLabel();
    });
  setLabel();
})();

(async () => {
  const status = document.getElementById("release-status");
  const grid = document.getElementById("release-assets");
  if (!status || !grid) return;
  const api = "https://api.github.com/repos/bspippi1337/blckchat/releases";
  const platform = name => {
    const n=name.toLowerCase();
    if(/\.apk$/.test(n))return "ANDROID";
    if(/\.ipa$/.test(n))return "IOS (SIGNED)";
    if(/\.dmg$|\.pkg$/.test(n))return "MACOS";
    if(/\.msi$|\.exe$/.test(n))return "WINDOWS";
    if(/\.appimage$|\.deb$|\.rpm$/.test(n))return "LINUX";
    if(/\.zip$|\.tar\.gz$|\.tar\.xz$/.test(n))return "ARCHIVE";
    return "OTHER";
  };
  try {
    const response=await fetch(api,{headers:{"Accept":"application/vnd.github+json"}});
    if(!response.ok)throw new Error("GitHub returned "+response.status);
    const releases=await response.json();
    const builds=releases.filter(r=>!r.draft && Array.isArray(r.assets) && r.assets.length);
    const latest=builds[0];
    if(!latest){status.textContent="No downloadable application builds have been published yet.";return;}
    status.textContent="Available release: "+latest.tag_name+". Select a verified release asset below.";
    for(const asset of latest.assets){
      if(!/^https:\/\/github\.com\/bspippi1337\/blckchat\/releases\/download\//.test(asset.browser_download_url))continue;
      const link=document.createElement("a");link.className="download";link.href=asset.browser_download_url;
      const label=document.createElement("span");label.className="platform";label.textContent=platform(asset.name);
      const name=document.createElement("strong");name.textContent=asset.name;
      const size=document.createElement("small");size.textContent=(asset.size/1048576).toFixed(1)+" MB · GitHub Release";
      link.append(label,name,size);grid.append(link);
    }
    if(!grid.childElementCount)status.textContent="Release metadata is available, but no matching download assets were found.";
  }catch(error){
    status.textContent="Release lookup is temporarily unavailable. Open GitHub Releases directly to check downloads.";
  }
})();
