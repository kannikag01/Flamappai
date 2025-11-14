function loadBase64Image() {
    fetch("image_b64.txt")
        .then(res => res.text())
        .then(base64 => {
            const img = document.getElementById("image") as HTMLImageElement;
            img.src = "data:image/png;base64," + base64.trim();
        })
        .catch(err => console.error("Failed to load base64 image:", err));
}

// Fake FPS update
const fpsSpan = document.getElementById("fps") as HTMLElement;
let fps = 12;
setInterval(() => {
    fps += (Math.random() > 0.5 ? 1 : -1);
    if (fps < 5) fps = 5;
    fpsSpan.innerText = fps.toString();
}, 800);

// Load the base64 file on start
loadBase64Image();
