function loadBase64Image() {
    fetch("image_b64.txt")
        .then(function (res) { return res.text(); })
        .then(function (base64) {
        var img = document.getElementById("image");
        img.src = "data:image/png;base64," + base64.trim();
    })
        .catch(function (err) { return console.error("Failed to load base64 image:", err); });
}
// Fake FPS update
var fpsSpan = document.getElementById("fps");
var fps = 12;
setInterval(function () {
    fps += (Math.random() > 0.5 ? 1 : -1);
    if (fps < 5)
        fps = 5;
    fpsSpan.innerText = fps.toString();
}, 800);
// Load the base64 file on start
loadBase64Image();
