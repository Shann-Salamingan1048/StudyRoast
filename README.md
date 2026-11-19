# StudyRoast 🎓🔥

**Study smarter with an AI tutor that explains, quizzes, and jokes with you.**

StudyRoast is an Android study companion designed to turn uploaded handouts into interactive learning experiences. It combines document-based multiple-choice quizzes, concept explanations, an animated AI avatar, and playful commentary to make reviewing feel more like studying with a friend.

> **Project status:** In development. The features below describe the project's intended experience; individual features may still be under implementation or testing.

## ✨ Features

- **📚 Handout library** — Import and organize study materials, including PDF, DOCX, and TXT files.
- **📝 AI quiz maker** — Create practice quizzes from uploaded handouts, with four answer choices per question, answer checking, and explanations.
- **🔎 Source-based learning** — Aim to keep questions and explanations grounded in the material you uploaded, with checks to reject unclear or unsupported questions.
- **🤖 Animated AI tutor** — An expressive companion that reacts to your answers and helps explain difficult topics.
- **🔥 Personality modes** — Choose **Friendly**, **Sarcastic**, **Savage**, or **Serious** feedback. The humor targets study mistakes, not the learner.
- **💬 Ask while studying** — Chat with the tutor about concepts or ask for hints without leaving the current activity.
- **🎙️ Voice interaction** — The planned voice experience supports spoken questions and tutor responses, subject to device capabilities.
- **🎓 Lecture mode** — Explore topics through simpler explanations, examples, and follow-up questions.
- **🧠 Teach the AI** — Practice explaining a concept in your own words and receive feedback on what you understood or missed.
- **📈 Progress tracking** — Review quiz results, revisit missed questions, and identify topics that need more practice.
- **📱 Offline-first goal** — Keep saved handouts, quizzes, and progress accessible offline, with a lightweight on-device AI tutor as the preferred long-term approach.

## 🚀 How StudyRoast Works

1. **Upload** a handout to your library.
2. **Review** the extracted content to confirm it was read correctly.
3. **Generate** a quiz or open a guided lecture based on that material.
4. **Answer** four-option multiple-choice questions and get explanations.
5. **Ask the AI tutor** for a hint, a simpler explanation, or a follow-up example.
6. **Review mistakes** and practice challenging topics again.

## 🛠️ Technology

StudyRoast is built as an Android project using:

- **Kotlin** for application logic
- **Jetpack Compose + Material 3** for the interface
- **Gradle** for building the Android application
- **Room / DataStore** for local persistence (as applicable to the implementation)
- **On-device AI inference** as the intended plug-and-play tutoring architecture
- **Android speech services or offline-capable alternatives** for voice features

## 🔒 AI, Privacy, and Cost

StudyRoast is designed to favor **free and open-source technology** and reduce dependence on paid AI APIs. The intended user experience is a dedicated, lightweight AI tutor with no API key or external AI server configuration required.

Small local AI models can still make factual mistakes. Generated quizzes and explanations should be checked against the original handout, especially for technical or high-stakes subjects. Offline voice support and performance may vary by device.

## 🎯 Project Goal

Make studying more engaging, memorable, and accessible by combining **active recall, document-grounded practice, personalized explanations, and a humorous AI study partner** in one Android application.

## 📌 Development Note

This README describes StudyRoast's product direction, not a guarantee that every listed feature is already complete. Check the current app build and project source for implemented functionality.

---

*StudyRoast — Learn it. Quiz it. Get roasted. Remember it.*
