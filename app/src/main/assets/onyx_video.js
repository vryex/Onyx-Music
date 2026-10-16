var onyxPlayer = null;
var onyxReady = false;
var onyxFailed = false;
var onyxLastProgressAt = 0;
var onyxLastPosition = null;
function onyxFail(reason) {
  if (onyxFailed) return;
  onyxFailed = true;
  console.log('ONYX_VIDEO_ERROR:' + reason);
}
function onYouTubeIframeAPIReady() {
  onyxPlayer = new YT.Player('player', {
    width: '100%', height: '100%', videoId: ONYX_VIDEO_ID,
    playerVars: { playsinline: 1, controls: 0, rel: 0, origin: ONYX_ORIGIN, widget_referrer: ONYX_ORIGIN },
    events: {
      onReady: function(e) { e.target.mute(); onyxReady = true; console.log('ONYX_VIDEO_READY'); },
      onError: function(e) { onyxFail(e.data); },
      onStateChange: function(e) { console.log(e.data === 1 ? 'ONYX_VIDEO_PLAYING' : 'ONYX_VIDEO_WAITING'); }
    }
  });
}
function onyxSync(position, playing, speed) {
  if (!onyxReady || !onyxPlayer || onyxFailed) return;
  onyxPlayer.mute();
  var rates = onyxPlayer.getAvailablePlaybackRates();
  if (rates.indexOf(speed) < 0) { onyxFail('rate'); return; }
  if (onyxPlayer.getPlaybackRate() !== speed) onyxPlayer.setPlaybackRate(speed);
  var state = onyxPlayer.getPlayerState();
  var current = onyxPlayer.getCurrentTime();
  var now = Date.now();
  if (!playing || (state === 1 && onyxLastPosition !== null && current > onyxLastPosition + 0.05)) {
    onyxLastProgressAt = now;
  } else if (onyxLastProgressAt === 0) {
    onyxLastProgressAt = now;
  } else if (now - onyxLastProgressAt > 20000) {
    onyxFail('stalled'); return;
  }
  if (Math.abs(current - position) > 1.5) {
    onyxPlayer.seekTo(position, true);
    // Exclude our own corrective seeks from playback progress detection.
    onyxLastPosition = position;
  } else {
    onyxLastPosition = current;
  }
  if (playing && state !== 1 && state !== 3) onyxPlayer.playVideo();
  if (!playing && state !== 2 && state !== 5) onyxPlayer.pauseVideo();
}
function onyxPause() { if (onyxReady && onyxPlayer) onyxPlayer.pauseVideo(); }
var tag = document.createElement('script');
tag.src = 'https://www.youtube.com/iframe_api';
tag.onerror = function() { onyxFail('network'); };
document.head.appendChild(tag);
