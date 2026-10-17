var onyxPlayer = null;
var onyxReady = false;
var onyxFailed = false;
var onyxLastProgressAt = 0;
var onyxLastPosition = null;
var onyxInitialSeekDone = false;
var onyxLastSeekAt = 0;
var onyxShown = false;

function onyxFail(reason) {
  if (onyxFailed) return;
  onyxFailed = true;
  console.log('ONYX_VIDEO_ERROR:' + reason);
}
function onYouTubeIframeAPIReady() {
  onyxPlayer = new YT.Player('player', {
    width: '100%', height: '100%', videoId: ONYX_VIDEO_ID,
    playerVars: {
      playsinline: 1, enablejsapi: 1, autoplay: 0, controls: 0, rel: 0,
      mute: 1, origin: ONYX_ORIGIN, widget_referrer: ONYX_ORIGIN
    },
    events: {
      onReady: function(e) {
        e.target.mute();
        onyxReady = true;
        console.log('ONYX_VIDEO_READY');
      },
      onError: function(e) { onyxFail(e.data); },
      onStateChange: function(e) {
        if (e.data !== 1) console.log('ONYX_VIDEO_WAITING');
      }
    }
  });
}
function onyxSync(position, playing, speed) {
  if (!onyxReady || !onyxPlayer || onyxFailed) return;
  try {
    onyxPlayer.mute();
    var rates = onyxPlayer.getAvailablePlaybackRates() || [];
    // YouTube can return an EMPTY list until the video's metadata is loaded.
    // Never classify normal 1x playback as unsupported during that phase.
    if (rates.length && rates.indexOf(speed) < 0 && Math.abs(speed - 1) > 0.01) {
      onyxFail('rate'); return;
    }
    if (rates.indexOf(speed) >= 0 && onyxPlayer.getPlaybackRate() !== speed) {
      onyxPlayer.setPlaybackRate(speed);
    }
    var state = onyxPlayer.getPlayerState();
    var current = onyxPlayer.getCurrentTime();
    var now = Date.now();
    if (!playing) {
      onyxLastProgressAt = 0;
      if (state !== 2 && state !== 5) onyxPlayer.pauseVideo();
      onyxLastPosition = current;
      return;
    }
    // Start video BEFORE doing timeline corrections: seeking on every poll while
    // the iframe is unstarted/buffering can keep it permanently black.
    if (state !== 1 && state !== 3) onyxPlayer.playVideo();
    if (state === 1 && !onyxInitialSeekDone) {
      onyxPlayer.seekTo(position, true);
      onyxInitialSeekDone = true;
      onyxLastSeekAt = now;
      onyxLastPosition = position;
      onyxLastProgressAt = now;
      return;
    }
    var progressed = state === 1 && onyxLastPosition !== null && current > onyxLastPosition + 0.08;
    if (progressed) {
      onyxLastProgressAt = now;
      if (!onyxShown) {
        onyxShown = true;
        var poster = document.getElementById('onyx-poster');
        if (poster) poster.style.display = 'none';
        console.log('ONYX_VIDEO_PLAYING');
      }
    } else if (onyxLastProgressAt === 0) {
      onyxLastProgressAt = now;
    } else if (now - onyxLastProgressAt > 14000) {
      onyxFail('stalled'); return;
    }
    // Do not interrupt buffering. Correct only large, sustained drift once
    // decoding has started, then allow 6s for the next frame to settle.
    if (state === 1 && onyxInitialSeekDone &&
        Math.abs(current - position) > 2.75 && now - onyxLastSeekAt > 6000) {
      onyxPlayer.seekTo(position, true);
      onyxLastSeekAt = now;
      onyxLastProgressAt = now;
      onyxLastPosition = position;
    } else {
      onyxLastPosition = current;
    }
  } catch (error) {
    onyxFail('runtime');
  }
}
function onyxPause() { if (onyxReady && onyxPlayer) onyxPlayer.pauseVideo(); }
var tag = document.createElement('script');
tag.src = 'https://www.youtube.com/iframe_api';
tag.onerror = function() { onyxFail('network'); };
document.head.appendChild(tag);
