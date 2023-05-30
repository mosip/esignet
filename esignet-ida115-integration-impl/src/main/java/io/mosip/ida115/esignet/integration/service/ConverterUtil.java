package io.mosip.ida115.esignet.integration.service;

import io.mosip.biometrics.util.ConvertRequestDto;
import io.mosip.biometrics.util.face.FaceDecoder;
import io.mosip.kernel.core.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConverterUtil {
	
	public static final String FACE_ISO_NUMBER = "ISO19794_5_2011";

	
//	static {
//		// load OpenCV library
////        nu.pattern.OpenCV.loadShared();
//		nu.pattern.OpenCV.loadLocally();
//        System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
//    }
	
	public static String convertJP2ToJpeg(String jp2Image) {
		try {
			ConvertRequestDto convertRequestDto = new ConvertRequestDto();
			convertRequestDto.setVersion(FACE_ISO_NUMBER);
			convertRequestDto.setInputBytes(CryptoUtil.decodePlainBase64(jp2Image));
			byte[] image = FaceDecoder.convertFaceISOToImageBytes(convertRequestDto);
			return CryptoUtil.encodeToPlainBase64(image);
		} catch(Exception exp) {
			log.error("Error Converting JP2 To JPEG. " + exp.getMessage(), exp);
		}
		return null;
	}

}
