package io.pinkspider.global.util;

import io.pinkspider.global.api.ApiStatus;
import io.pinkspider.global.constants.FileUtilConstants;
import io.pinkspider.global.exception.FileException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

public class FileUtils {

    @Value("util.file-util.path")
    private static String MONEY_MOVE_DIR;

    public static void uploadFile(String filePath, String fileName, MultipartFile file) {
        byte[] bytes = null;

        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new FileException(ApiStatus.FILE_IO_EXCEPTION.getResultCode(), ApiStatus.FILE_IO_EXCEPTION.getResultMessage());
        }
        checkFileSize(bytes);
        File uploadFileInfo = preCheckPathAndFile(filePath, fileName, FileUtilConstants.PROCESS_TYPE_UPLOAD);
        uploadProcess(uploadFileInfo, bytes);
    }

    public static byte[] downloadFile(String filePath, String fileName) {
        File downloadFile = preCheckPathAndFile(filePath, fileName, FileUtilConstants.PROCESS_TYPE_DOWNLOAD);
        return downloadProcess(downloadFile);
    }

    private static File preCheckPathAndFile(String filePath, String fileName, int processType) {
        String directoryPath = MONEY_MOVE_DIR + filePath;
        checkDirectoryExist(directoryPath, processType);

        File file = new File(directoryPath + fileName);
        checkFileExist(file, processType);

        return file;
    }

    private static void checkFileSize(byte[] file) {
        if (FileUtilConstants.DEFAULT_FILE_LIMIT_SIZE < file.length) {
            throw new FileException(ApiStatus.EXCEEDED_FILE_SIZE.getResultCode(), ApiStatus.EXCEEDED_FILE_SIZE.getResultMessage());
        }
    }

    private static void checkDirectoryExist(String checkPath, int processType) {
        File uploadPath = new File(checkPath);
        if (!uploadPath.isDirectory() && processType == FileUtilConstants.PROCESS_TYPE_UPLOAD) {
            uploadPath.mkdirs();
        } else if (!uploadPath.isDirectory() && processType == FileUtilConstants.PROCESS_TYPE_DOWNLOAD) {
            throw new FileException(ApiStatus.FILE_NOT_EXIST.getResultCode(), ApiStatus.FILE_NOT_EXIST.getResultMessage());
        }
    }

    private static void checkFileExist(File checkFile, int processType) {
        if (checkFile.exists() && processType == FileUtilConstants.PROCESS_TYPE_UPLOAD) {
            throw new FileException(ApiStatus.FILE_ALREADY_EXIST.getResultCode(), ApiStatus.FILE_ALREADY_EXIST.getResultMessage());
        } else if (!checkFile.exists() && processType == FileUtilConstants.PROCESS_TYPE_DOWNLOAD) {
            throw new FileException(ApiStatus.FILE_NOT_EXIST.getResultCode(), ApiStatus.FILE_NOT_EXIST.getResultMessage());
        }
    }

    private static void uploadProcess(File uploadInfo, byte[] file) {
        try (OutputStream out = new FileOutputStream(uploadInfo)) {
            out.write(file);
        } catch (IOException e) {
            throw new FileException(ApiStatus.FILE_UPLOAD_PROCESS_ERROR.getResultCode(), ApiStatus.FILE_UPLOAD_PROCESS_ERROR.getResultMessage());
        }
    }

    private static byte[] downloadProcess(File downloadFile) {
        try {
            return Files.readAllBytes(downloadFile.toPath());
        } catch (IOException e) {
            throw new FileException(ApiStatus.FILE_NOT_EXIST.getResultCode(), ApiStatus.FILE_NOT_EXIST.getResultMessage());
        }
    }
}
