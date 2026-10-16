const fs = require('fs'), vm = require('vm'), assert = require('assert');
let time = 0, state = 2, rate = 1, calls=[];
const player = {mute(){calls.push('mute')},getCurrentTime(){return time},seekTo(t){time=t;calls.push('seek')},getAvailablePlaybackRates(){return [1,1.5,2]},getPlaybackRate(){return rate},setPlaybackRate(r){rate=r},getPlayerState(){return state},playVideo(){state=1;calls.push('play')},pauseVideo(){state=2;calls.push('pause')}};
const context = {ONYX_VIDEO_ID:'dQw4w9WgXcQ',ONYX_ORIGIN:'https://com.onyx.music',console:{log(){}},document:{createElement(){return {}},head:{appendChild(){}}},YT:{Player:function(id,options){options.events.onReady({target:player});return player;}}};
vm.createContext(context);vm.runInContext(fs.readFileSync('app/src/main/assets/onyx_video.js','utf8'),context);context.onYouTubeIframeAPIReady();
context.onyxSync(25,true,1.5);assert.equal(time,25);assert.equal(state,1);assert.equal(rate,1.5);
calls=[];context.onyxSync(25.2,true,1.5);assert(!calls.includes('seek'),'Small playback drift should not cause repeated seeks');
context.onyxSync(25.2,false,1.5);assert.equal(state,2);context.onyxSync(60,true,3);assert.equal(rate,1.5,'Unsupported rates must not be applied');context.onyxPause();assert.equal(state,2);
console.log('PASS: video sync, small drift, pause, speed validation and lifecycle pause');
