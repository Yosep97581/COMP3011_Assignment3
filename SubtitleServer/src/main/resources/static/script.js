// AI-assisted: initial microphone recording and upload logic generated with ChatGPT, 
// then reviewed and tested by me in the browser and on TITAN.

const startButton = document.getElementById("startButton");
const stopButton = document.getElementById("stopButton");
const statusText = document.getElementById("status");
const audioPlayback = document.getElementById("audioPlayback");
const transcriptionText = document.getElementById("transcription");

let mediaRecorder;
let audioChunks = [];
let audioUrl;

startButton.addEventListener("click", async () => {
    try {
        // Ask the user for permission to access the microphone.
        const stream = await navigator.mediaDevices.getUserMedia({
            audio: true
        });

        mediaRecorder = new MediaRecorder(stream);

        // Clear any previous recording data.
        audioChunks = [];

        // Store the recorded audio data as it becomes available.
        mediaRecorder.addEventListener("dataavailable", event => {
            audioChunks.push(event.data);
        });

        mediaRecorder.addEventListener("stop", async () => {
            // Combine the recorded chunks into one audio file in memory.
            const audioBlob = new Blob(audioChunks, {
                type: mediaRecorder.mimeType
            });

            // Create a temporary URL so the recording can still be played back.
			if (audioUrl) {
			    URL.revokeObjectURL(audioUrl);
			}

			audioUrl = URL.createObjectURL(audioBlob);
            audioPlayback.src = audioUrl;

            // Release the microphone after recording has finished.
            stream.getTracks().forEach(track => track.stop());

            await uploadAudio(audioBlob);
        });

        mediaRecorder.start();

        statusText.textContent = "Recording...";
        startButton.disabled = true;
        stopButton.disabled = false;

    } catch (error) {
        // Show a simple message if microphone access fails.
        statusText.textContent = "Could not access microphone.";
        console.error(error);
    }
});

stopButton.addEventListener("click", () => {
    // Only stop if a recording is currently active.
    if (mediaRecorder && mediaRecorder.state === "recording") {
        mediaRecorder.stop();

        startButton.disabled = false;
        stopButton.disabled = true;
    }
});

async function uploadAudio(audioBlob) {
    statusText.textContent = "Transcribing recording...";

    // FormData allows the audio Blob to be sent like a normal uploaded file.
    const formData = new FormData();
    formData.append("audio", audioBlob, "recording.webm");

    try {
        const response = await fetch("/api/v1/transcribe", {
            method: "POST",
            body: formData
        });

        const data = await response.json();

        if (!response.ok) {
            statusText.textContent =
                    data.message || "Transcription failed.";
            return;
        }

        // Display the text returned by the speech-to-text service.
        transcriptionText.textContent = data.text;
        statusText.textContent = "Transcription complete.";

    } catch (error) {
        statusText.textContent = "Could not connect to the server.";
        console.error(error);
    }
}