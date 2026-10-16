var onyxPlayer = null;
var onyxReady = false;
function onYouTubeIframeAPIReady() {
  onyxPlayer = new YT.Player('player', {
    width: '100%', height: '100%', videoId: ONYX_VIDEO_ID,
    playerVars: { playsinline: 1, controls: 0, rel: 0, origin: ONYX_ORIGIN, widget_referrer: ONYX_ORIGIN },
    events: {
      onReady: function(e) { e.target.mute(); onyxReady = true; console.log('ONYX_VIDEO_READY'); },
      onError: function(e) { console.log('ONYX_VIDEO_ERROR:' + e.data); },
      onStateChange: function(e) { if (e.data === 1) console.log('ONYX_VIDEO_PLAYING'); }
    }
  });
}
function onyxSync(position, playing, speed) {
  if (!onyxReady || !onyxPlayer) return;
  onyxPlayer.mute();
  if (Math.abs(onyxPlayer.getCurrentTime() - position) > 1.5) onyxPlayer.seekTo(position, true);
  var rates = onyxPlayer.getAvailablePlaybackRates();
  if (rates.indexOf(speed) >= 0 && onyxPlayer.getPlaybackRate() !== speed) onyxPlayer.setPlaybackRate(speed);
  var state = onyxPlayer.getPlayerState();
  if (playing && state !== 1 && state !== 3) onyxPlayer.playVideo();
  if (!playing && state !== 2 && state !== 5) onyxPlayer.pauseVideo();
}
function onyxPause() { if (onyxReady && onyxPlayer) onyxPlayer.pauseVideo(); }
var tag = document.createElement('script');
tag.src = 'https://www.youtube.com/iframe_api';
tag.onerror = function() { console.log('ONYX_VIDEO_ERROR:network'); };
document.head.appendChild(tag);
