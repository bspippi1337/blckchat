"use strict";
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
