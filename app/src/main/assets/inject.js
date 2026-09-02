/**
 * YouTube Music Shielded Core Engine v1.0.3
 * Total Ad-Shielding (YouTubei API JSON Sanitization + Fetch/XHR Proxy + DOM Blocker)
 * Brave-Grade Background Playback (Page Visibility API Hook + RenderWidget Bypass)
 * Studio WebAudio 5-Band Equalizer & Native Android Bridge
 */
(function () {
    if (window.__braveShieldCoreActive) return;
    window.__braveShieldCoreActive = true;

    // =========================================================================
    // 1. PAGE VISIBILITY & BACKGROUND AUDIO LOCKDOWN (SCREEN-OFF ENGINE)
    // =========================================================================
    try {
        const fakeVisible = () => 'visible';
        const fakeFalse = () => false;
        const fakeTrue = () => true;

        Object.defineProperty(document, 'hidden', { get: fakeFalse, set: () => {}, configurable: true });
        Object.defineProperty(document, 'visibilityState', { get: fakeVisible, set: () => {}, configurable: true });
        Object.defineProperty(document, 'webkitHidden', { get: fakeFalse, set: () => {}, configurable: true });
        Object.defineProperty(document, 'webkitVisibilityState', { get: fakeVisible, set: () => {}, configurable: true });
        document.hasFocus = fakeTrue;

        // Block visibility change and blur event listeners
        const origAddEventListener = EventTarget.prototype.addEventListener;
        EventTarget.prototype.addEventListener = function (type, listener, options) {
            if (
                type === 'visibilitychange' ||
                type === 'webkitvisibilitychange' ||
                type === 'pagehide' ||
                type === 'blur' ||
                type === 'freeze'
            ) {
                return; // Suppress YouTube visibility pause triggers
            }
            return origAddEventListener.call(this, type, listener, options);
        };

        window.onblur = null;
        document.onvisibilitychange = null;
    } catch (e) {
        console.error('[BraveShield] Visibility Hook Error:', e);
    }

    // =========================================================================
    // 2. YOUTUBEI API JSON AD-PURGER (FETCH & XHR INTERCEPTOR)
    // =========================================================================
    function sanitizePlayerResponse(obj) {
        if (!obj || typeof obj !== 'object') return obj;

        // Purge ad placements, slots, and video ads
        delete obj.adPlacements;
        delete obj.adSlots;
        delete obj.playerAds;
        delete obj.adBreakHeartbeatParams;

        if (obj.playbackTracking) {
            delete obj.playbackTracking.videostatsPlaybackUrl;
            delete obj.playbackTracking.videostatsDelayplayUrl;
            delete obj.playbackTracking.videostatsWatchtimeUrl;
            delete obj.playbackTracking.videostatsQoeUrl;
            delete obj.playbackTracking.ptrackingUrl;
            delete obj.playbackTracking.qoeUrl;
            delete obj.playbackTracking.atrUrl;
        }

        return obj;
    }

    // Proxy window.fetch
    const origFetch = window.fetch;
    window.fetch = async function (...args) {
        const response = await origFetch.apply(this, args);
        const url = (args[0] && typeof args[0] === 'string') ? args[0] : (args[0] && args[0].url) ? args[0].url : '';

        if (url.includes('/youtubei/v1/player') || url.includes('/youtubei/v1/next')) {
            try {
                const clone = response.clone();
                const json = await clone.json();
                const cleaned = sanitizePlayerResponse(json);
                return new Response(JSON.stringify(cleaned), {
                    status: response.status,
                    statusText: response.statusText,
                    headers: response.headers
                });
            } catch (err) {
                return response;
            }
        }
        return response;
    };

    // Proxy XMLHttpRequest
    const origOpen = XMLHttpRequest.prototype.open;
    const origSend = XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open = function (method, url, ...rest) {
        this._url = url;
        return origOpen.call(this, method, url, ...rest);
    };

    XMLHttpRequest.prototype.send = function (body) {
        if (this._url && (this._url.includes('/youtubei/v1/player') || this._url.includes('/youtubei/v1/next'))) {
            this.addEventListener('readystatechange', function () {
                if (this.readyState === 4 && this.status === 200) {
                    try {
                        const data = JSON.parse(this.responseText);
                        const sanitized = sanitizePlayerResponse(data);
                        Object.defineProperty(this, 'responseText', { value: JSON.stringify(sanitized), configurable: true });
                        Object.defineProperty(this, 'response', { value: JSON.stringify(sanitized), configurable: true });
                    } catch (e) {}
                }
            });
        }
        return origSend.call(this, body);
    };

    // Intercept ytInitialPlayerResponse
    let rawInitialPlayerResponse = window.ytInitialPlayerResponse;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
        get: () => rawInitialPlayerResponse,
        set: (val) => {
            rawInitialPlayerResponse = sanitizePlayerResponse(val);
        },
        configurable: true
    });

    // =========================================================================
    // 3. AMOLED BLACK THEME & PROMO SUPPRESSION CSS
    // =========================================================================
    const amoledStyle = document.createElement('style');
    amoledStyle.id = 'brave-amoled-theme';
    amoledStyle.textContent = `
        :root, html, body, ytmusic-app, ytmusic-app-layout, ytmusic-browse-response,
        ytmusic-player-page, ytmusic-nav-bar, ytmusic-player-bar, ytmusic-search-box,
        #player-bar-background, #nav-bar-background, .background-gradient,
        ytmusic-item-section-renderer, ytmusic-section-list-renderer,
        ytmusic-immersive-header-renderer, ytmusic-background-overlay-renderer {
            background-color: #000000 !important;
            background: #000000 !important;
            --ytmusic-color-black1: #000000 !important;
            --ytmusic-color-black2: #050505 !important;
            --ytmusic-color-black3: #0A0A0A !important;
            --ytmusic-color-black4: #121212 !important;
            --ytmusic-overlay-background-color: rgba(0, 0, 0, 0.95) !important;
        }

        ytmusic-mealbar-promo-renderer,
        ytmusic-upsell-dialog-renderer,
        ytmusic-guide-promo-entry-renderer,
        ytmusic-banner-promo-renderer,
        .ytmusic-popup-container ytmusic-mealbar-promo-renderer,
        ytmusic-pivot-bar-item-renderer[tab-id="SPunlimited"],
        #upsell-dialog,
        ytmusic-toast-item-renderer:has(a[href*="premium"]),
        tp-yt-paper-dialog:has(a[href*="premium"]),
        .video-ads,
        .ytp-ad-module,
        .ytp-ad-overlay-container,
        ytmusic-ad-slot-renderer {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            opacity: 0 !important;
            pointer-events: none !important;
        }

        ytmusic-player-bar {
            border-top: 1px solid #181818 !important;
        }
    `;

    function applyAmoledTheme() {
        if (!document.getElementById('brave-amoled-theme')) {
            (document.head || document.documentElement).appendChild(amoledStyle);
        }
    }
    applyAmoledTheme();
    document.addEventListener('DOMContentLoaded', applyAmoledTheme);

    // =========================================================================
    // 4. INSTANT AD FAST-FORWARD & SKIP BUTTON AUTOMATION
    // =========================================================================
    function killAds() {
        const player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
        const video = document.querySelector('video');

        if (player && player.classList && player.classList.contains('ad-showing')) {
            if (video) {
                video.muted = true;
                if (!isNaN(video.duration) && video.duration > 0) {
                    video.currentTime = video.duration;
                }
                video.playbackRate = 16.0;
            }
            const skipButton = document.querySelector(
                '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton, button.ytp-ad-skip-button-icon'
            );
            if (skipButton) {
                skipButton.click();
            }
        }

        const overlayAds = document.querySelectorAll(
            '.ytp-ad-overlay-close-button, .ytp-ad-message-container, .ytp-ad-action-interstitial'
        );
        overlayAds.forEach(btn => {
            if (typeof btn.click === 'function') btn.click();
            else btn.remove();
        });
    }

    setInterval(killAds, 50);

    const observer = new MutationObserver(() => {
        killAds();
        applyAmoledTheme();
        initWebAudioEqualizer();
    });

    observer.observe(document.documentElement, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['class']
    });

    // =========================================================================
    // 5. WEBAUDIO STUDIO-GRADE 5-BAND EQUALIZER DSP PIPELINE
    // =========================================================================
    let audioCtx = null;
    let sourceNode = null;
    let eqFilters = [];
    let bassBoostFilter = null;
    let preampGainNode = null;
    let isEqInitialized = false;

    const bandFrequencies = [60, 230, 910, 3600, 14000];

    function initWebAudioEqualizer() {
        if (isEqInitialized) return;
        const video = document.querySelector('video');
        if (!video) return;

        try {
            const AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (!AudioContextClass) return;

            if (!audioCtx) {
                audioCtx = new AudioContextClass();
            }

            if (!sourceNode && video) {
                sourceNode = audioCtx.createMediaElementSource(video);

                bassBoostFilter = audioCtx.createBiquadFilter();
                bassBoostFilter.type = 'lowshelf';
                bassBoostFilter.frequency.value = 80;
                bassBoostFilter.gain.value = 0;

                eqFilters = bandFrequencies.map((freq, index) => {
                    const filter = audioCtx.createBiquadFilter();
                    if (index === 0) {
                        filter.type = 'lowshelf';
                    } else if (index === bandFrequencies.length - 1) {
                        filter.type = 'highshelf';
                    } else {
                        filter.type = 'peaking';
                        filter.Q.value = 1.4;
                    }
                    filter.frequency.value = freq;
                    filter.gain.value = 0;
                    return filter;
                });

                preampGainNode = audioCtx.createGain();
                preampGainNode.gain.value = 1.0;

                let currentNode = sourceNode;
                currentNode.connect(bassBoostFilter);
                currentNode = bassBoostFilter;

                eqFilters.forEach(filter => {
                    currentNode.connect(filter);
                    currentNode = filter;
                });

                currentNode.connect(preampGainNode);
                preampGainNode.connect(audioCtx.destination);

                isEqInitialized = true;
            }
        } catch (e) {}
    }

    ['click', 'touchstart', 'keydown'].forEach(evt => {
        document.addEventListener(evt, function () {
            if (audioCtx && audioCtx.state === 'suspended') {
                audioCtx.resume();
            }
            initWebAudioEqualizer();
        }, { once: true });
    });

    // =========================================================================
    // 6. METADATA & PLAYBACK STATE SYNCHRONIZATION
    // =========================================================================
    let lastState = {
        title: '',
        artist: '',
        album: '',
        artUrl: '',
        isPlaying: false,
        duration: 0,
        position: 0
    };

    let intentionalNativePause = false;

    function notifyNativeBridge() {
        if (!window.AndroidBridge) return;

        const video = document.querySelector('video');
        const isPlaying = video ? !video.paused && !video.ended : false;
        const duration = video && !isNaN(video.duration) ? Math.floor(video.duration) : 0;
        const position = video && !isNaN(video.currentTime) ? Math.floor(video.currentTime) : 0;

        let title = '';
        let artist = '';
        let album = '';
        let artUrl = '';

        if (navigator.mediaSession && navigator.mediaSession.metadata) {
            const meta = navigator.mediaSession.metadata;
            title = meta.title || '';
            artist = meta.artist || '';
            album = meta.album || '';
            if (meta.artwork && meta.artwork.length > 0) {
                artUrl = meta.artwork[meta.artwork.length - 1].src || '';
            }
        }

        if (!title) {
            const titleEl = document.querySelector('ytmusic-player-bar .title');
            if (titleEl) title = titleEl.textContent.trim();
        }
        if (!artist) {
            const bylineEl = document.querySelector('ytmusic-player-bar .byline');
            if (bylineEl) {
                const parts = bylineEl.textContent.split('•').map(s => s.trim());
                if (parts.length > 0) artist = parts[0];
                if (parts.length > 1) album = parts[1];
            }
        }
        if (!artUrl) {
            const imgEl = document.querySelector('ytmusic-player-bar img#img') || document.querySelector('ytmusic-player-bar .image');
            if (imgEl && imgEl.src) artUrl = imgEl.src;
        }

        if (
            lastState.title !== title ||
            lastState.artist !== artist ||
            lastState.isPlaying !== isPlaying ||
            Math.abs(lastState.position - position) >= 2 ||
            lastState.duration !== duration
        ) {
            lastState = { title, artist, album, artUrl, isPlaying, duration, position };
            try {
                window.AndroidBridge.onPlaybackStateChanged(
                    isPlaying,
                    title,
                    artist,
                    album,
                    artUrl,
                    duration,
                    position
                );
            } catch (e) {}
        }
    }

    function attachMediaListeners() {
        const video = document.querySelector('video');
        if (!video) return;

        ['play', 'playing', 'timeupdate', 'ended', 'loadedmetadata', 'seeking', 'seeked'].forEach(evt => {
            video.removeEventListener(evt, notifyNativeBridge);
            video.addEventListener(evt, notifyNativeBridge);
        });

        video.addEventListener('pause', function () {
            // Auto-resume if screen turned off unexpectedly
            if (!intentionalNativePause && lastState.isPlaying && !video.ended) {
                setTimeout(() => {
                    if (video.paused && !intentionalNativePause) {
                        video.play();
                    }
                }, 50);
            }
            notifyNativeBridge();
        });
    }

    setInterval(attachMediaListeners, 1000);

    // =========================================================================
    // 7. EXPOSED CONTROL APIS
    // =========================================================================
    window.bravePlayer = {
        play: function () {
            intentionalNativePause = false;
            const video = document.querySelector('video');
            if (audioCtx && audioCtx.state === 'suspended') {
                audioCtx.resume();
            }
            if (video && video.paused) {
                video.play();
            } else {
                const playBtn = document.querySelector('#play-pause-button') || document.querySelector('.play-pause-button');
                if (playBtn) playBtn.click();
            }
        },
        pause: function () {
            intentionalNativePause = true;
            const video = document.querySelector('video');
            if (video && !video.paused) {
                video.pause();
            } else {
                const playBtn = document.querySelector('#play-pause-button') || document.querySelector('.play-pause-button');
                if (playBtn) playBtn.click();
            }
        },
        togglePlay: function () {
            const video = document.querySelector('video');
            if (video) {
                if (video.paused) this.play();
                else this.pause();
            }
        },
        next: function () {
            intentionalNativePause = false;
            const nextBtn = document.querySelector('.next-button') || document.querySelector('#next-button');
            if (nextBtn) nextBtn.click();
        },
        previous: function () {
            intentionalNativePause = false;
            const prevBtn = document.querySelector('.previous-button') || document.querySelector('#previous-button');
            if (prevBtn) prevBtn.click();
        },
        seekTo: function (seconds) {
            const video = document.querySelector('video');
            if (video && !isNaN(seconds)) {
                video.currentTime = seconds;
            }
        },
        setVolume: function (volumePercent) {
            const video = document.querySelector('video');
            if (video) {
                video.volume = Math.max(0, Math.min(1, volumePercent));
            }
        },
        setEqualizer: function (bandGainsArray, bassBoostGain, preampGain) {
            initWebAudioEqualizer();
            if (audioCtx && audioCtx.state === 'suspended') {
                audioCtx.resume();
            }

            if (eqFilters && eqFilters.length === 5 && Array.isArray(bandGainsArray)) {
                for (let i = 0; i < 5; i++) {
                    if (i < bandGainsArray.length) {
                        eqFilters[i].gain.value = Number(bandGainsArray[i]) || 0;
                    }
                }
            }

            if (bassBoostFilter && typeof bassBoostGain === 'number') {
                bassBoostFilter.gain.value = Number(bassBoostGain) || 0;
            }

            if (preampGainNode && typeof preampGain === 'number') {
                preampGainNode.gain.value = Number(preampGain) || 1.0;
            }
        }
    };

    notifyNativeBridge();
})();
