package ee.mt.flipnicexplorer;

import javafx.animation.KeyValue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        if (length == 0) {
            length = (int) (this.memory.length - start);
        }
        return this.ReadData(start, length);
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
        return this.endOfFile - start;
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
        this.InitTOC();
    }

    private void InitTOC() {
        byte[] buffer;
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
            long addr = 0x800L * (buffer[0x3C] & 0xFF) + 0x80000L * (buffer[0x3D] & 0xFF) + 0x8000000L * (buffer[0x3E] & 0xFF) + 0x800000000L * (buffer[0x3F] & 0xFF);
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

    public Long GetRootOffset(String fileName) {
        return this.fileTable.get(fileName);
    }

    public Long GetFolderOffset(String fileName, String folder) {
        byte[] fldr = GetFile(folder);
        HashMap<String, Long> folderContents = GetFolderTOCbyData(fldr);
        return folderContents.get(fileName) + this.GetRootOffset(folder);
    }

    public void RenameRootFile(String fileName, String newName) {
        int idx = 0;
        for (String file: this.fileTable.keySet()) {
            if (file.equals(fileName)) {
                break;
            }
            idx++;
        }
        int TOC_OFFSET = (idx + 1) * 0x40;
        byte[] fileNameData = newName.getBytes(StandardCharsets.US_ASCII);
        byte[] pushData = new byte[0x3C];
        System.arraycopy(fileNameData,0,pushData,0,fileNameData.length);
        this.WriteBytes(pushData, (long) TOC_OFFSET);
        this.fileTable.clear();
        this.InitTOC();
    }

    public void RenameFolderFile(String fileName, String newName, String folderName) {
        HashMap<String, Long> folderTree = this.GetFolderTOCbyData(this.GetFile(folderName));
        int initialOffset = Math.toIntExact(GetRootOffset(folderName));
        int idx = 0;
        for (String file: folderTree.keySet()) {
            if (file.equals(fileName)) {
                break;
            }
            idx++;
        }
        int TOC_OFFSET = idx * 0x40 + initialOffset;
        byte[] fileNameData = newName.getBytes(StandardCharsets.US_ASCII);
        byte[] pushData = new byte[0x3C];
        System.arraycopy(fileNameData,0,pushData,0,fileNameData.length);
        this.WriteBytes(pushData, (long) TOC_OFFSET);
        this.fileTable.clear();
        this.InitTOC();
    }

    private void WriteBytes(byte[] data, Long offset) {
        for (long idx = offset; idx < offset + data.length; idx++) {
            this.memory[(int) idx] = data[(int) (idx - offset)];
        }
    }

    public String GetNiceFileType(String fileName) {
        if (fileName.endsWith("\\")) {
            return "Folder";
        }
        switch (fileName.split("\\.")[1]) {
            case "MSG":
                return "String data";
            case "SST":
                return "Stage data/configuration";
            case "MID":
                return "Music sequence";
            case "HD":
                return "Sound data (header)";
            case "BD":
                return "Sound data (body)";
            case "LP4":
                return "3D model";
            case "COL":
                return "Collision data";
            case "FPD":
                return "AI movement sequence";
            case "FPC":
                return "Camera animation sequence";
            case "MLB":
                return "Menu layout";
            case "TM2":
                return "Texture file";
            case "PSS":
                return "INT/IPU streams (video)";
            case "SVAG":
                return "Compressed Sony ADPCM audio";
            case "ICO":
                return "Save icon";
            case "VSD":
                return "Vibration configuration";
            case "LAY":
                return "Positional data";
            case "LIT":
                return "Lighting data";
            case "CSV":
                return "Comma Separated Values";
            case "XML":
                return "Extensible Markup Language file";
            case "FTL":
                return "Texture list file";
            case "TXT":
                return "Plain-text file";
            default:
                return "Unknown";
        }
    }

}
