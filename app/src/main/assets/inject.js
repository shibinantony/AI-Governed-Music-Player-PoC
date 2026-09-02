/**
 * YouTube Music Shielded Core JavaScript Engine
 * Handles ad fast-forwarding, DOM stripping, AMOLED injection, native state synchronization,
 * Brave-grade background playback enforcement (Page Visibility & Lifecycle bypass), and WebAudio Equalizer DSP.
 */
(function () {
    if (window.__braveShieldInitialized) return;
    window.__braveShieldInitialized = true;

    // -------------------------------------------------------------
    // 1. Brave-Grade Background Playback & Page Visibility Spoofing
    // -------------------------------------------------------------
    try {
        // Permanently spoof document visibility so YouTube Music never knows the screen is off
        Object.defineProperty(document, 'hidden', {
            get: function () { return false; },
            configurable: true
        });
        Object.defineProperty(document, 'visibilityState', {
            get: function () { return 'visible'; },
            configurable: true
        });
        Object.defineProperty(document, 'webkitVisibilityState', {
            get: function () { return 'visible'; },
            configurable: true
        });
        Object.defineProperty(document, 'webkitHidden', {
            get: function () { return false; },
            configurable: true
        });

        // Intercept and neutralize visibility change, pagehide, and blur event listeners
        const originalAddEventListener = EventTarget.prototype.addEventListener;
        EventTarget.prototype.addEventListener = function (type, listener, options) {
            if (
                type === 'visibilitychange' ||
                type === 'webkitvisibilitychange' ||
                type === 'pagehide' ||
                type === 'freeze'
            ) {
                // Trap and suppress YouTube's visibility pause triggers
                return;
            }
            return originalAddEventListener.call(this, type, listener, options);
        };

        // Suppress window.onblur from pausing audio
        window.onblur = null;
        document.onvisibilitychange = null;
    } catch (e) {
        console.error('[BraveShield] Visibility spoof error:', e);
    }

    // -------------------------------------------------------------
    // 2. AMOLED Dark Theme & Style Customization
    // -------------------------------------------------------------
    const amoledStyle = document.createElement('style');
    amoledStyle.id = 'brave-amoled-theme';
    amoledStyle.textContent = `
        /* Enforce pure AMOLED black (#000000) across all YouTube Music containers */
        :root,
        html,
        body,
        ytmusic-app,
        ytmusic-app-layout,
        ytmusic-browse-response,
        ytmusic-player-page,
        ytmusic-nav-bar,
        ytmusic-player-bar,
        ytmusic-search-box,
        #player-bar-background,
        #nav-bar-background,
        .background-gradient,
        ytmusic-item-section-renderer,
        ytmusic-section-list-renderer,
        ytmusic-immersive-header-renderer,
        ytmusic-background-overlay-renderer {
            background-color: #000000 !important;
            background: #000000 !important;
            --ytmusic-color-black1: #000000 !important;
            --ytmusic-color-black2: #050505 !important;
            --ytmusic-color-black3: #0A0A0A !important;
            --ytmusic-color-black4: #121212 !important;
            --ytmusic-overlay-background-color: rgba(0, 0, 0, 0.95) !important;
        }

        /* Suppress promo elements, upsell banners, and premium trial modals */
        ytmusic-mealbar-promo-renderer,
        ytmusic-upsell-dialog-renderer,
        ytmusic-guide-promo-entry-renderer,
        ytmusic-banner-promo-renderer,
        .ytmusic-popup-container ytmusic-mealbar-promo-renderer,
        ytmusic-pivot-bar-item-renderer[tab-id="SPunlimited"],
        #upsell-dialog,
        ytmusic-toast-item-renderer:has(a[href*="premium"]),
        tp-yt-paper-dialog:has(a[href*="premium"]) {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            opacity: 0 !important;
            pointer-events: none !important;
        }

        /* Optimize player bar touch targets */
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

    // -------------------------------------------------------------
    // 3. Ad-Block & Video Fast-Forward Interceptor
    // -------------------------------------------------------------
    function killAds() {
        const player = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
        const video = document.querySelector('video');

        // Check if player indicates an active ad
        if (player && player.classList && player.classList.contains('ad-showing')) {
            if (video && !isNaN(video.duration) && video.duration > 0) {
                // Instantly advance video to the end and accelerate rate
                video.currentTime = video.duration;
                video.playbackRate = 16.0;
            }
            // Trigger skip button if present
            const skipButton = document.querySelector(
                '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton'
            );
            if (skipButton) {
                skipButton.click();
            }
        }

        // Close overlay ad cards
        const overlayAds = document.querySelectorAll(
            '.ytp-ad-overlay-close-button, .ytp-ad-message-container, .ytp-ad-action-interstitial'
        );
        overlayAds.forEach(btn => {
            if (typeof btn.click === 'function') btn.click();
            else btn.remove();
        });
    }

    setInterval(killAds, 250);

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

    // -------------------------------------------------------------
    // 4. WebAudio Studio-Grade Equalizer DSP Pipeline
    // -------------------------------------------------------------
    let audioCtx = null;
    let sourceNode = null;
    let eqFilters = [];
    let bassBoostFilter = null;
    let preampGainNode = null;
    let isEqInitialized = false;

    // Standard 5-band frequencies: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
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

                // Create Bass Boost Low-shelf filter
                bassBoostFilter = audioCtx.createBiquadFilter();
                bassBoostFilter.type = 'lowshelf';
                bassBoostFilter.frequency.value = 80;
                bassBoostFilter.gain.value = 0;

                // Create 5 peaking filters
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

                // Create Preamp Gain node
                preampGainNode = audioCtx.createGain();
                preampGainNode.gain.value = 1.0;

                // Connect Chain: source -> bassBoost -> filter[0] -> ... -> filter[4] -> preamp -> destination
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
        } catch (e) {
            // AudioContext already connected or cross-origin handled
        }
    }

    // Auto unlock audio context on user interaction
    ['click', 'touchstart', 'keydown'].forEach(evt => {
        document.addEventListener(evt, function () {
            if (audioCtx && audioCtx.state === 'suspended') {
                audioCtx.resume();
            }
            initWebAudioEqualizer();
        }, { once: true });
    });

    // -------------------------------------------------------------
    // 5. Media State & Metadata Bridge
    // -------------------------------------------------------------
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
            } catch (e) {
                console.error('[BraveShield] Bridge notification failed:', e);
            }
        }
    }

    // Attach listeners to video element
    function attachMediaListeners() {
        const video = document.querySelector('video');
        if (!video) return;

        ['play', 'playing', 'timeupdate', 'ended', 'loadedmetadata', 'seeking', 'seeked'].forEach(evt => {
            video.removeEventListener(evt, notifyNativeBridge);
            video.addEventListener(evt, notifyNativeBridge);
        });

        video.addEventListener('pause', function () {
            // Background auto-resume safeguard: If pause happened unexpectedly without user intention
            if (!intentionalNativePause && lastState.isPlaying && !video.ended) {
                setTimeout(() => {
                    if (video.paused && !intentionalNativePause) {
                        video.play();
                    }
                }, 100);
            }
            notifyNativeBridge();
        });
    }

    setInterval(attachMediaListeners, 1000);

    // -------------------------------------------------------------
    // 6. Exposed Control Interface (Callable from Native Kotlin)
    // -------------------------------------------------------------
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
            // bandGainsArray: e.g. [0, 2, 4, 2, 0] in dB (-12 to +12)
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
                // Preamp gain in multiplier (0.5 to 2.0)
                preampGainNode.gain.value = Number(preampGain) || 1.0;
            }
        }
    };

    notifyNativeBridge();
})();
