/**
 * YouTube Music Shielded Core JavaScript Engine
 * Handles ad fast-forwarding, DOM stripping, AMOLED injection, and native state synchronization.
 */
(function () {
    if (window.__braveShieldInitialized) return;
    window.__braveShieldInitialized = true;

    // -------------------------------------------------------------
    // 1. AMOLED Dark Theme & Style Customization
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
    // 2. Ad-Block & Video Fast-Forward Interceptor
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

    // High frequency ad check loop + MutationObserver for instant response
    setInterval(killAds, 250);

    const observer = new MutationObserver(() => {
        killAds();
        applyAmoledTheme();
    });

    observer.observe(document.documentElement, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['class']
    });

    // -------------------------------------------------------------
    // 3. Media State & Metadata Bridge
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

        // Strategy 1: Extract from MediaSession
        if (navigator.mediaSession && navigator.mediaSession.metadata) {
            const meta = navigator.mediaSession.metadata;
            title = meta.title || '';
            artist = meta.artist || '';
            album = meta.album || '';
            if (meta.artwork && meta.artwork.length > 0) {
                artUrl = meta.artwork[meta.artwork.length - 1].src || '';
            }
        }

        // Strategy 2: Fallback to DOM elements if MediaSession is incomplete
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

        // Only send updates when state actually changes to save IPC overhead
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

        ['play', 'pause', 'timeupdate', 'ended', 'loadedmetadata', 'seeking', 'seeked'].forEach(evt => {
            video.removeEventListener(evt, notifyNativeBridge);
            video.addEventListener(evt, notifyNativeBridge);
        });
    }

    setInterval(attachMediaListeners, 1000);

    // -------------------------------------------------------------
    // 4. Exposed Control Interface (Callable from Native Kotlin)
    // -------------------------------------------------------------
    window.bravePlayer = {
        play: function () {
            const video = document.querySelector('video');
            if (video && video.paused) {
                video.play();
            } else {
                const playBtn = document.querySelector('#play-pause-button') || document.querySelector('.play-pause-button');
                if (playBtn) playBtn.click();
            }
        },
        pause: function () {
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
                if (video.paused) video.play();
                else video.pause();
            }
        },
        next: function () {
            const nextBtn = document.querySelector('.next-button') || document.querySelector('#next-button');
            if (nextBtn) nextBtn.click();
        },
        previous: function () {
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
            // volumePercent: 0.0 to 1.0
            const video = document.querySelector('video');
            if (video) {
                video.volume = Math.max(0, Math.min(1, volumePercent));
            }
        },
        toggleAudioOnly: function () {
            const video = document.querySelector('video');
            if (video) {
                video.style.visibility = 'hidden';
            }
        }
    };

    // Initialize state
    notifyNativeBridge();
})();
