package com.afterme.backend.global.file;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;

public final class AttachmentFilename {
    private AttachmentFilename() {}

    public static String sanitize(String originalFilename) {
        if (originalFilename == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String path = originalFilename.replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty() || name.length() > 255 || name.equals(".") || name.equals("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return name;
    }
}
