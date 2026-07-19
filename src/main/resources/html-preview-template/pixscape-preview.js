/*
 * Copyright 2026 Laurent Robbens
 * SPDX-License-Identifier: Apache-2.0
 * @license magnet:?xt=urn:btih:8e4f440f4c65981c5bf93c76d35135ba5064d8b7&dn=apache-2.0.txt Apache-2.0
 */

function focusGameCanvas(evt) {
  var root = document.getElementById('embed-html');
  var canvas = root ? root.querySelector('canvas') : document.querySelector('canvas');

  if (canvas) {
    canvas.setAttribute('tabindex', '0');
    canvas.focus();
  }

  if (evt) {
    evt.preventDefault();
    evt.stopPropagation();
  }
}

function blockPreviewKeys(evt) {
  var keys = [
    'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown',
    '+', '-', '=', 'F9'
  ];

  if (keys.indexOf(evt.key) >= 0) {
    evt.preventDefault();
    evt.stopPropagation();
  }
}

window.addEventListener('load', function () {
  var root = document.getElementById('embed-html');

  if (root) {
    root.addEventListener('mousedown', focusGameCanvas, false);
    root.addEventListener('mouseup', function (evt) {
      evt.preventDefault();
      evt.stopPropagation();
    }, false);
  }

  document.addEventListener('keydown', blockPreviewKeys, false);
  document.addEventListener('keyup', blockPreviewKeys, false);
});

/* @license-end */
