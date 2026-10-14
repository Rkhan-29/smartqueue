package com.smartqueue.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.smartqueue.config.AppProperties;
import com.smartqueue.exception.ApiException;
import com.smartqueue.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/** Generates queue QR codes. The payload is only the public join URL containing the opaque public reference. */
@Service
@RequiredArgsConstructor
public class QrCodeService {

    private final AppProperties props;

    public String joinUrl(String publicRef) {
        String base = props.publicBaseUrl().replaceAll("/+$", "");
        return base + "/join/" + publicRef;
    }

    public byte[] png(String publicRef, int size) {
        int px = Math.min(Math.max(size, 128), 1024);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(joinUrl(publicRef), BarcodeFormat.QR_CODE, px, px,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 2,
                            EncodeHintType.CHARACTER_SET, "UTF-8"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
