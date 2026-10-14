// download blobs support
if (!window.tvBroClicksListener) {
    window.tvBroClicksListener = function(e) {
        if (e.target && e.target.tagName && e.target.tagName.toUpperCase() == "A" && e.target.attributes && e.target.attributes.href && e.target.attributes.href.value && e.target.attributes.href.value.toLowerCase().startsWith("blob:")) {
            var fileName = e.target.download;
            var url = e.target.attributes.href.value;
            var xhr = new XMLHttpRequest();
            xhr.open('GET', url, true);
            xhr.responseType = 'blob';
            xhr.onload = function(e) {
                if (this.status == 200) {
                    var blob = this.response;
                    var reader = new FileReader();
                    reader.readAsDataURL(blob);
                    reader.onloadend = function() {
                        var base64data = reader.result;
                        BrowserApp.takeBlobDownloadData(base64data, fileName, url, blob.type);
                    }
                }
            };
            xhr.send();
            e.stopPropagation();
            e.preventDefault();
        }
    };
    document.addEventListener("click", window.tvBroClicksListener);
}

// media playback control support (driven by the media / DPAD keys handled in MainActivity)
// NOTE: deliberately NOT a HTMLMediaElement.prototype patch - injecting a getter into a platform
// prototype changes every page script on the site and can be detected / re-defined by the host page.
function tvBroMediaIsPlaying(m) {
    return !!(m && m.currentTime > 0 && !m.paused && !m.ended && m.readyState > 2);
}

// The element the media keys should drive: whatever is actually playing, else the first one.
function tvBroActiveMedia() {
    var list = document.querySelectorAll('video, audio');
    for (var i = 0; i < list.length; i++) {
        if (tvBroMediaIsPlaying(list[i])) {
            return list[i];
        }
    }
    return list.length > 0 ? list[0] : null;
}

window.tvBroTogglePlayback = function() {
    var media = tvBroActiveMedia();
    if (!media) {
        return;
    }
    if (tvBroMediaIsPlaying(media)) {
        media.pause();
    } else {
        var p = media.play();
        // Autoplay policies reject the promise; swallow it instead of logging an uncaught error.
        if (p && typeof p.catch === 'function') {
            p.catch(function() {});
        }
    }
}

window.tvBroStopPlayback = function() {
    var media = tvBroActiveMedia();
    if (media) {
        media.pause();
        media.currentTime = 0;
    }
}

window.tvBroRewind = function() {
    var media = tvBroActiveMedia();
    if (media) {
        media.currentTime -= 10;
    }
}

window.tvBroFastForward = function() {
    var media = tvBroActiveMedia();
    if (media) {
        media.currentTime += 10;
    }
}

// context menu support
window.addEventListener("touchstart", function(e) {
    if (e.touches && e.touches.length > 0) {
        window.TVBRO_activeElement = e.target;
        window.TVBRO_touchStartX = e.touches[0].clientX;
        window.TVBRO_touchStartY = e.touches[0].clientY;
    }
});

// Real Desktop Mode fitting for TV Browser
(function() {
    var ua = navigator.userAgent || "";
    var isDesktop = ua.indexOf("Windows NT") !== -1 || ua.indexOf("X11; Linux x86_64") !== -1 || ua.indexOf("Macintosh") !== -1;
    if (isDesktop) {
        var metas = document.querySelectorAll('meta[name="viewport"]');
        for (var i = 0; i < metas.length; i++) {
            metas[i].parentNode.removeChild(metas[i]);
        }
    }
})();

// Force Enable Zoom: Ensure pages can never disable pinch or user scaling
(function() {
    function ensureZoomable() {
        var metas = document.querySelectorAll('meta[name="viewport"]');
        for (var i = 0; i < metas.length; i++) {
            var content = metas[i].getAttribute('content') || '';
            if (/user-scalable\s*=\s*no/i.test(content) || /maximum-scale\s*=\s*1(\.0)?/i.test(content)) {
                var updated = content
                    .replace(/user-scalable\s*=\s*no/gi, 'user-scalable=yes')
                    .replace(/maximum-scale\s*=\s*1(\.0)?/gi, 'maximum-scale=5.0');
                metas[i].setAttribute('content', updated);
            }
        }
    }
    ensureZoomable();
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', ensureZoomable);
    }
})();

