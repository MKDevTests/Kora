-- Moves every install that never picked a URL of its own onto the v6.1 bundle.
-- The download field is prefilled with it, so nobody has to paste a link to get
-- the models the reader actually loads.
--
-- A URL the user typed themselves is left alone: only the three we ever shipped
-- as the default are rewritten.
UPDATE ImageReaderSettings
SET rapid_ocr_models_url = 'https://github.com/MKDevTests/Kora/releases/download/model-v6.1/RapidOcrModels-v6.1.zip'
WHERE rapid_ocr_models_url IN (
    'https://github.com/MKDevTests/Kora/releases/download/model-v6/RapidOcrModels-v6.zip',
    'https://github.com/eserero/Sipurra/releases/download/model/RapidOcrModels.zip',
    'https://github.com/Snd-R/komelia-onnxruntime/releases/download/model/RapidOcrModels.zip'
);
