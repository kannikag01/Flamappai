"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const imgElement = document.getElementById("frame");
const statsElement = document.getElementById("stats");
const sample_base64_1 = __importDefault(require("./sample_base64"));
// Show image
imgElement.src = sample_base64_1.default;
// Show some text
statsElement.innerText = "Source: Android processed frame\nFormat: PNG\nDisplayed via TypeScript viewer";
