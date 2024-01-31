package ee.mt.flipnicexplorer;

import javafx.animation.KeyValue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.*;

public class FlipnicFilesystem {
    HashMap<String, Long> fileTable = new LinkedHashMap<String, Long>();
    byte[] memory;
    long streamStart = 0;
    long endOfFile = 0;
    public FlipnicFilesystem(String filePath) throws IOException {
        this.LoadFiles(filePath);
    }

    private byte[] ReadData(Long start, int length) {
        byte[] returnData = new byte[length];
        for (Long i = start; i < start + length; i++) {
            returnData[(int)(i - start)] = this.memory[Integer.valueOf(Math.toIntExact(i))];
        }
        return returnData;
    }

    public List<String> GetRootTOC() {
        return new ArrayList<>(this.fileTable.keySet());
    }

    public byte[] GetFile(String name) {
        Long start = this.fileTable.get(name);
        int length = 0;
        boolean nextValue = false;
        for (Long l: fileTable.values()) {
            if (l.equals(start)) {
                nextValue = true;
            } else if (nextValue) {
                length = (int) (l - (int)Integer.valueOf(Math.toIntExact(start)));
                break;
            }
        }
        return this.ReadData(start, (int)length);
    }

    public List<String> GetFolderTOC(String name) {
        HashMap<String, Long> folderData = GetFolderTOCbyData(this.GetFile(name));
        return new ArrayList<>(folderData.keySet());
    }

    public void SaveFile(String ffsFile, String outputFile) throws IOException {
        byte[] fileData = GetFile(ffsFile);
        File output = new File(outputFile);
        try (FileOutputStream outputStream = new FileOutputStream(output);) {
            outputStream.write(fileData);
        }
    }

    public void SaveFileOnFolder(String folder, String ffsFile, String outputFile) throws IOException {
        if (folder.startsWith("\\")) {
            folder = folder.substring(1);
        }
        byte[] folderData = GetFile(folder);
        HashMap<String, Long> folderFiles = GetFolderTOCbyData(folderData);
        Long startIdx = 0L;
        int length = 0;
        boolean foundFile = false;
        for (String key: folderFiles.keySet()) {
            if (foundFile) {
                length = (int) (folderFiles.get(key) - startIdx);
                break;
            }
            if (key.equals(ffsFile)) {
                startIdx = folderFiles.get(key);
                foundFile = true;
            }
        }
        byte[] folderFile = Arrays.copyOfRange(folderData, Math.toIntExact(startIdx), (int) (startIdx + length));
        File output = new File(outputFile);
        try (FileOutputStream outputStream = new FileOutputStream(output)) {
            outputStream.write(folderFile);
        }
    }

    public Long GetSize(String fileName, HashMap<String, Long>... fileList) {
        HashMap<String, Long> files = fileList.length > 0 ? fileList[0] : this.fileTable;
        Long start = 0L;
        boolean fileFound = false;
        for (String file: files.keySet()) {
            if (fileFound) {
                return ((files.get(file) - start));
            }
            if (file.equals(fileName)) {
                start = files.get(file);
                fileFound = true;
            }
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    public Long GetFolderFileSize(String fileName, String folderName) {
        return GetSize(fileName, GetFolderTOCbyData(GetFile(folderName)));
    }

    public HashMap<String, Long> GetFolderTOCbyData (byte[] data) {
        HashMap<String, Long> returnData = new LinkedHashMap<>();
        long eof = 0;
        for (int i = 0x0; i < data.length; i+=0x40) {
            byte[] buffer = Arrays.copyOfRange(data, i, i + 0x40);
            StringBuilder entryStr = new StringBuilder();
            int l = 0;
            for (byte b: buffer) {
                if (b == 0x00) {
                    break;
                }
                entryStr.append((char)b);
            }
            long addr = (buffer[0x3C] & 0xFF) + 0x100L * (buffer[0x3D] & 0xFF) + 0x10000L * (buffer[0x3E] & 0xFF) + 0x1000000L * (buffer[0x3F] & 0xFF);
            switch (entryStr.toString()) {
                case "*End Of Mem Data":
                    eof = addr;
                    break;
                default:
                    //System.out.println(entryStr + " at " + addr);
                    returnData.put(entryStr.toString(), addr);
                    break;
            }
            if (eof > 0) {
                break;
            }
        }
        return returnData;
    }

    private void LoadFiles(String filePath) throws IOException {
        this.memory = Files.readAllBytes(Path.of(filePath));
        byte[] buffer = new byte[0x40];
        boolean endOfToc = false;
        int i = 0;
        while (!endOfToc) {
            buffer = this.ReadData((long)i, 0x40);
            StringBuilder entryStr = new StringBuilder();
            int l = 0;
            for (byte b: buffer) {
                if (b == 0x00) {
                    break;
                }
                entryStr.append((char)b);
            }
            long addr = 0x800l * (buffer[0x3C] & 0xFF) + 0x80000l * (buffer[0x3D] & 0xFF) + 0x8000000l * (buffer[0x3E] & 0xFF) + 0x800000000l * (buffer[0x3F] & 0xFF);
            switch (entryStr.toString()) {
                case "*End Of CD Data":
                    endOfToc = true;
                    this.endOfFile = addr;
                    break;
                case "*Top Of CD Data":
                    this.streamStart = addr;
                    break;
                default:
                    //System.out.println(entryStr + " at " + addr);
                    fileTable.put(entryStr.toString(), addr);
                    break;
            }
            i += 0x40;
            if (i > this.memory.length) {
                endOfToc = true;
            }
        }


    }

    public String GetNiceSize(Long size) {
        DecimalFormat df = new DecimalFormat("###.##");
        if (size < 1000L) {
            return size + " B";
        } else if (size < 1000000L) {
            return df.format((float)size / 1000f) + " kB";
        } else if (size < 1000000000L) {
            return df.format((float)size / 1000000f) + " MB";
        } else {
            return df.format((float)size / 1000000000f) + " GB";
        }
    }

}
