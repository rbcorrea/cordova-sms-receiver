
var argscheck = require('cordova/argscheck'),
    exec = require('cordova/exec');

var exports = {};
exports.startWatch = function(success, failure) {
	cordova.exec( success, failure, 'SMS', 'startWatch', [] );
};

exports.stopWatch = function(success, failure) {
	cordova.exec( success, failure, 'SMS', 'stopWatch', [] );
};

exports.enableIntercept = function(switcher, success, failure) {
	switcher = !! switcher;
	cordova.exec( success, failure, 'SMS', 'enableIntercept', [ switcher ] );
};

module.exports = exports;

