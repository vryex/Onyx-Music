const fs = require('fs'), vm = require('vm'), assert = require('assert');
let time = 0, state = 2, rate = 1, calls=[], events=[], clock=0;
const player = {mute(){calls.push('mute')},getCurrentTime(){return time},seekTo(t){time=t;calls.push('seek')},getAvailablePlaybackRates(){return [1,1.5,2]},getPlaybackRate(){return rate},setPlaybackRate(r){rate=r},getPlayerState(){return state},playVideo(){state=1;calls.push('play')},pauseVideo(){state=2;calls.push('pause')}};
const context = {ONYX_VIDEO_ID:'dQw4w9WgXcQ',ONYX_ORIGIN:'https://com.onyx.music',Date:{now(){return clock}},console:{log(message){events.push(message)}},document:{createElement(){return {}},getElementById(){return {style:{}}},head:{appendChild(){}}},YT:{Player:function(id,options){options.events.onReady({target:player});return player;}}};
vm.createContext(context);vm.runInContext(fs.readFileSync('app/src/main/assets/onyx_video.js','utf8'),context);context.onYouTubeIframeAPIReady();
context.onyxSync(25,true,1.5);assert.equal(time,0);assert.equal(state,1);assert.equal(rate,1.5);
context.onyxSync(25,true,1.5);assert.equal(time,25);
assert(!events.includes('ONYX_VIDEO_PLAYING'),'Starting the iframe is not evidence of rendered video');
time=25.6;context.onyxSync(25.6,true,1.5);
assert(events.includes('ONYX_VIDEO_PLAYING'),'Only advancing playback should reveal the video');
calls=[];context.onyxSync(25.2,true,1.5);assert(!calls.includes('seek'),'Small playback drift should not cause repeated seeks');
context.onyxSync(25.2,false,1.5);assert.equal(state,2);context.onyxSync(60,true,1.1);assert(events.some(e=>e.includes('ERROR:rate')),'Unsupported speed must return to artwork instead of repeatedly seeking');context.onyxPause();assert.equal(state,2);
// A second isolated frame becomes ready but never starts moving.
context.onyxFailed=false;context.onyxLastProgressAt=0;events=[];clock=1000;state=3;context.onyxSync(60,true,1);clock=26000;context.onyxSync(60,true,1);assert(events.some(e=>e.includes('ERROR:stalled')),'Stuck buffering must return to artwork');
console.log('PASS: video sync, small drift, pause, speed validation and lifecycle pause');
