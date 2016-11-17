import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.csv.CSVPrinter;

public class Indexes {
    public enum Type {
        FILE,
        FOLDER,
        /**
         * Seems to be used to store >1 face for a single image.
         */
        REFERENCE;
    }

    /** Basename of files; full path of folders; empty string for references. */
    ArrayList<String> names;

    /** Full path to file or folder. */
    ArrayList<String> fullnames;

    /** Index within the database, or {@link #folderIndex} for references (TODO: why?) */
    ArrayList<Long> indexes;
    /** Index of parent, or {@link #folderIndex} for folders */
    ArrayList<Long> originalIndexes;
    /** Type of this database entry */
    ArrayList<Type> types;
    private final File folder;
    final Long folderIndex = 0xFFFFFFFFL;
    long entries;
    
    public Indexes(File folder) {
        names = new ArrayList<String>();
        fullnames = new ArrayList<String>();
        indexes = new ArrayList<Long>();
        originalIndexes = new ArrayList<Long>();
        types = new ArrayList<>();
        this.folder = folder;
	}
    
    public void Populate() throws Exception{
    	DataInputStream din = new DataInputStream(new BufferedInputStream(new FileInputStream(new File(folder, "thumbindex.db"))));
        @SuppressWarnings("unused")
		long magic = ReadFunctions.readUnsignedInt(din); //file start with a magic sequence
        entries = ReadFunctions.readUnsignedInt(din); // then number of entries

        System.out.println("nb entries: "+entries);
        
        String path;
        long index;

       for(int i=0;i<entries;i++){
           
            path = ReadFunctions.getString(din);  // null terminated string
            ReadFunctions.read26(din);            // followed by 26 garbaged bytes
            index = ReadFunctions.readUnsignedInt(din); // followed by the index (0xFFFFFFFF for folder)
            
            
            names.add(path);
            indexes.add((long)i);
            originalIndexes.add(index);
            
            
            if(path.equals("") && index != folderIndex){   //empty file name (deleted), change index to 0xFFFFFFFF
                indexes.set(i, folderIndex);
                types.add(Type.REFERENCE);
            } else if (index == folderIndex) {
                types.add(Type.FOLDER);
            } else {
                types.add(Type.FILE);
            }

       }
        din.close();

        for (int i = 0; i < entries; i++) {
            fullnames.add(getFullPath(i));
        }
    }

    private String getFullPath(int i) {
        String name = names.get(i);

        switch (types.get(i)) {
            case FOLDER:
                return name;
            case FILE:
            case REFERENCE:
                return getFullPath(originalIndexes.get(i).intValue()) + name;
            default:
                // Unpossible
                return null;
        }
    }
    
    public void writeCSV(File output) throws IOException{
        FileWriter fw = new FileWriter(new File(output, "indexes.csv"));
        BufferedWriter bw = new BufferedWriter(fw);
        CSVPrinter csv = new CSVPrinter(bw, PMPDB.CSV_FORMAT.withHeader("Index", "Original Indexes", "type", "Image Path"));

        for(int i=0; i<entries; i++){
            Long originalIndex = originalIndexes.get(i);
            String name = fullnames.get(i);
            Type type = types.get(i);

            csv.printRecord(i, originalIndex, type.name(), name);
        }
        csv.close();
    }
    
    @SuppressWarnings("static-access")
	public static void main(String []args) throws Exception{
        EnvironmentVariables.StandardArguments a = EnvironmentVariables.parseCommandLine("Indexes", null, args);
        
        Indexes indexes = new Indexes(a.folder);
        indexes.Populate();
        indexes.writeCSV(a.output);
    }
}
