# react-native-vosk - React ASR (Automated Speech Recognition)

Speech recognition module for react native using [Vosk](https://github.com/alphacep/vosk-api) library

## Installation

### Library

```sh
npm install -S react-native-vosk
```

### Models

Vosk uses prebuilt models to perform speech recognition offline. You have to download the model(s) that you need on [Vosk official website](https://alphacephei.com/vosk/models)
Avoid using too heavy models, because the computation time required to load them into your app could lead to bad user experience.
Then, unzip the model in your app folder. If you just need to use the iOS version, put the model folder wherever you want, and import it as described below. If you need both iOS and Android to work, you can avoid to copy the model twice for both projects by importing the model from the Android assets folder in XCode.

**Experimental**: Loading a model dynamically into the app storage, aside from the main bundle is a new and experimental feature. Would love for you all to test, and let us know if it is a viable option. If you choose to download a model to your app’s storage (preferably internal), you can pass the model directory path when calling `vosk.loadModel(path)`.

To download and load a model as part of an app's Main Bundle, just do as follows:

### Android

**Starting from version 1.0.0**, the model is not searched in the android project by default anymore.

In your root project folder, create a folder `assets` (next to the `src` one) and put your model folder in it. The path should be like this: `assets/model-en-en` if you downloaded the english model for example.

_Important_: The model folder must be directly in the `assets` folder, not in a subfolder. If you have multiple models, you can put them all in the `assets` folder like this:

```assets/
  model-en-en/
  model-fr-fr/
  model-de-de/
```

You can import as many models as you want. If your model folder does not start with `model-`, you won't be able to load it. The model folder must contain all the files provided in the zip you downloaded from Vosk website. If you don't have the `assets` folder in your project, just create it.

If you have any trouble, double check you are following the naming convention, or check the example project provided with the library : [example](https://github.com/riderodd/react-native-vosk/tree/main/example)

### iOS

In XCode, click on your App on the projects pannel, then go to the `Build phases` tab of settings pannel. Scroll down to the `Copy bundle resources` accordion. Click on the `+` button at the end of the list. Click on the `Add other...` button at the bottom of the prompt window.

<a href="https://raw.githubusercontent.com/riderodd/react-native-vosk/main/docs/xcode_add_files_to_folder.png" target="_blank" rel="noopener noreferer"><img src="https://raw.githubusercontent.com/riderodd/react-native-vosk/main/docs/xcode_add_files_to_folder.png" alt="XCode add files to project" width="200" /></a>

Then navigate to your model folder. You can navigate to the assets folder you may have created for android, and chose your model here. It will avoid to have the model copied twice in your project. If you don't use the Android build, you can just put the model wherever you want, and select it. Click on `Open`.

<a href="https://raw.githubusercontent.com/riderodd/react-native-vosk/main/docs/xcode_chose_model_folder.png" target="_blank" rel="noopener noreferer"><img src="https://raw.githubusercontent.com/riderodd/react-native-vosk/main/docs/xcode_chose_model_folder.png" alt="XCode chose model folder" width="200" /></a>

Check `Copy items if needed`. If you want to avoid having the model living twice in your app folders in order to reduce your bundle size, select `Create folder references`. That's all. The model folder should appear in your project. When you click on it, your project target should be checked (see below).

<a href="https://raw.githubusercontent.com/riderodd/react-native-vosk/main/docs/xcode_full_settings_screenshot.png" target="_blank" rel="noopener noreferer"><img src="https://raw.githubusercontent.com/riderodd/react-native-vosk/main/docs/xcode_full_settings_screenshot.png" alt="XCode full settings screenshot" width="200" /></a>

**_Microphone permission_**
Don't forget to add the microphone permission to your `Info.plist` file if you haven't already:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>We need access to your microphone for speech recognition</string>
```

Or in XCode, open your `Info.plist` file, hover the last line and click on the `+` button that appears. Select `Privacy - Microphone Usage Description` in the dropdown list. In the value field, enter a message that will be displayed to the user when the system asks for microphone permission.

### Expo (Config Plugin)

This library ships with an optional Expo config plugin so you can use it in managed or prebuild workflows.

Add the plugin in your `app.json` (or `app.config.js`):

```json
{
  "expo": {
    "plugins": [
      [
        "react-native-vosk",
        {
          "models": ["assets/model-fr-fr", "assets/model-en-en"],
          "iOSMicrophonePermission": "Nous avons besoin d'accéder à votre microphone"
        }
      ]
    ]
  }
}
```

Options (all optional):

- `models`: Array of relative paths (from project root) to model folders. They will:
  - On iOS: be copied into the Xcode project root and added as resources.
  - On Android: be passed as a Gradle property (`Vosk_models`) so UUID generation copies them into the library's generated assets.
    Ensure each folder is a valid Vosk model directory.
- `iOSMicrophonePermission`: String used for `NSMicrophoneUsageDescription`.

Notes:

- If `models` is omitted, Android falls back to legacy scanning of an adjacent `assets` folder for `model-*` directories (bare workflow behavior).
- Bare React Native users can continue to integrate models manually; Expo is not required.
- After changing plugin config run `npx expo prebuild` (or `expo prebuild -p ios|android`) to regenerate native projects.

## Usage

```js
import Vosk from 'react-native-vosk';

// ...

const vosk = new Vosk();

vosk
  .loadModel('model-en-en')
  .then(() => {
    const options = {
      grammar: ['left', 'right', '[unk]'],
    };

    vosk
      .start(options)
      .then(() => {
        console.log('Recognizer successfuly started');
      })
      .catch((e) => {
        console.log('Error: ' + e);
      });

    const resultEvent = vosk.onResult((res) => {
      console.log('A onResult event has been caught: ' + res);
    });

    // Don't forget to call resultEvent.remove(); to delete the listener
  })
  .catch((e) => {
    console.error(e);
  });
```

Note that `start()` method will ask for audio record permission.

[See complete example...](https://github.com/riderodd/react-native-vosk/blob/main/example/src/App.tsx)

## Loading via Path

- Primarily intended for models that are not included in the app’s Main Bundle.

### Preliminary Steps

- Use a file system package to download and store a model from remote location
  - [react-native-file-access](https://www.npmjs.com/package/react-native-file-access) is one that we found to be stable, but this is a personal preference based on use

```js
import Vosk from 'react-native-vosk';

// ...

const vosk = new Vosk();

const path = 'some/path/to/model/directory';

vosk
  .loadModel(path)
  .then(() => {
    const options = {
      grammar: ['left', 'right', '[unk]'],
    };

    vosk
      .start(options)
      .then(() => {
        console.log('Recognizer successfuly started');
      })
      .catch((e) => {
        console.log('Error: ' + e);
      });

    const resultEvent = vosk.onResult((res) => {
      console.log('A onResult event has been caught: ' + res);
    });

    // Don't forget to call resultEvent.remove(); to delete the listener
  })
  .catch((e) => {
    console.error(e);
  });
```

### Methods

| Method      | Argument                         | Return          | Description                                                                           |
| ----------- | -------------------------------- | --------------- | ------------------------------------------------------------------------------------- |
| `loadModel` | `path: string`                   | `Promise<void>` | Loads the voice model used for recognition, it is required before using start method. |
| `start`     | `options: VoskOptions` or `none` | `Promise<void>` | Starts the recognizer, an `onResult()` event will be fired.                           |
| `stop`      | `none`                           | `none`          | Stops the recognizer. Listener should receive final result if there is any.           |
| `unload`    | `none`                           | `none`          | Unloads the model, also stops the recognizer.                                         |

### Types

| VoskOptions | Type       | Required | Description                                                                                                                                  |
| ----------- | ---------- | -------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `grammar`   | `string[]` | No       | Set of phrases the recognizer will seek on which is the closest one from the record, add `"[unk]"` to the set to recognize phrases striclty. |
| `timeout`   | `int`      | No       | Timeout in milliseconds to listen.                                                                                                           |

### Events

| Method            | Promise return                                      | Description                                          |
| ----------------- | --------------------------------------------------- | ---------------------------------------------------- |
| `onPartialResult` | The recognized word as a `string`                   | Called when partial recognition result is available. |
| `onResult`        | The recognized word as a `string`                   | Called after silence occured.                        |
| `onFinalResult`   | The recognized word as a `string`                   | Called after stream end, like a `stop()` call        |
| `onError`         | The error that occured as a `string` or `exception` | Called when an error occurs                          |
| `onTimeout`       | `void`                                              | Called after timeout expired                         |

### Examples

#### Default

```js
vosk.start().then(() => {
  const resultEvent = vosk.onResult((res) => {
    console.log('A onResult event has been caught: ' + res);
  });
});

// when done, remember to call resultEvent.remove();
```

#### Using grammar

```js
vosk
  .start({
    grammar: ['left', 'right', '[unk]'],
  })
  .then(() => {
    const resultEvent = vosk.onResult((res) => {
      if (res === 'left') {
        console.log('Go left');
      } else if (res === 'right') {
        console.log('Go right');
      } else {
        console.log("Instruction couldn't be recognized");
      }
    });
  });

// when done, remember to call resultEvent.remove();
```

#### Using timeout

```js
vosk
  .start({
    timeout: 5000,
  })
  .then(() => {
    const resultEvent = vosk.onResult((res) => {
      console.log('An onResult event has been caught: ' + res);
    });

    const timeoutEvent = vosk.onTimeout(() => {
      console.log('Recognizer timed out');
    });
  });

// when done, remember to clean all listeners;
```

#### [Complete example](https://github.com/riderodd/react-native-vosk/blob/main/example/src/App.tsx)

## Transcription

You can transcribe an audio file or raw PCM data using `transcribeFile`.

### File path

```js
vosk
  .transcribeFile('/path/to/file.wav')
  .then((res) => {
    console.log(res);
  })
  .catch((e) => {
    console.error(e);
  });
```

### Raw PCM Data (Base64)

```js
const base64Data = '...'; // Your base64 encoded PCM data
vosk
  .transcribeFile(base64Data, { isRawData: true })
  .then((res) => {
    console.log(res);
  })
  .catch((e) => {
    console.error(e);
  });
```

### Raw PCM Data (Byte Array)

```js
const pcmBytes = [0, 10, ...]; // Your byte array
vosk
  .transcribeFile(pcmBytes)
  .then((res) => {
    console.log(res);
  })
  .catch((e) => {
    console.error(e);
  });
```

## Streaming API

The streaming API allows you to progressively feed PCM audio chunks to the recognizer, useful for real-time audio processing from microphones or other audio sources.

### Basic Usage

```js
// Load model first
await vosk.loadModel('model-en-us');

// Start streaming session
await vosk.startStreaming();

// Feed audio chunks progressively
for (const chunk of audioChunks) {
  await vosk.feedChunk(chunk); // chunk is number[] (byte array)
}

// Stop and get final result
const result = await vosk.stopStreaming();
console.log(JSON.parse(result).text);
```

### With Grammar

```js
await vosk.startStreaming({
  grammar: ['yes', 'no', 'maybe', '[unk]']
});

// Feed chunks...
const result = await vosk.stopStreaming();
```

### Events

The streaming API emits the same events as the regular recording API:

```js
await vosk.startStreaming();

// Listen for partial results while streaming
vosk.onPartialResult((partial) => {
  console.log('Partial:', partial);
});

// Listen for final results (on silence detection)
vosk.onResult((result) => {
  console.log('Result:', result);
});

// Feed chunks...
await vosk.stopStreaming();
```

### Methods

| Method | Arguments | Return | Description |
| --- | --- | --- | --- |
| `startStreaming` | `options?: VoskOptions` | `Promise<string>` | Starts a streaming session. Optional grammar parameter. |
| `feedChunk` | `data: number[]` | `Promise<boolean>` | Feeds a PCM audio chunk (byte array). Emits events on results. |
| `stopStreaming` | none | `Promise<string>` | Stops streaming and returns final Vosk JSON result. |

### Audio Format

- **Sample rate**: 16 kHz
- **Bit depth**: 16-bit PCM
- **Channels**: Mono
- **Encoding**: Little-endian
- **Format**: Raw PCM bytes as `number[]` (0-255)

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
