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
	//will store the name of the folders or the name of the image file (the index in the list will be the correct index of the image file)
    ArrayList<String> names;  
    
    //will store 0xFFFFFFFF for folder, the index of the folder for image files
    ArrayList<Long> indexes;
    ArrayList<Long> originalIndexes ;
    private final File folder;
    Long folderIndex = new Long(4294967295L);
    long entries;
    
    public Indexes(File folder) {
    	names = new ArrayList<String>();  
        indexes = new ArrayList<Long>();
        originalIndexes = new ArrayList<Long>();
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
        Long folderIndex = new Long(4294967295L); //0xFFFFFFFF
        
       for(int i=0;i<entries;i++){
           
            path = ReadFunctions.getString(din);  // null terminated string
            ReadFunctions.read26(din);            // followed by 26 garbaged bytes
            index = ReadFunctions.readUnsignedInt(din); // followed by the index (0xFFFFFFFF for folder)
            
            
            names.add(path);
            indexes.add((long)i);
            originalIndexes.add(index);
            
            
            if(path.equals("")){   //empty file name (deleted), change index to 0xFFFFFFFF
                indexes.set(i, folderIndex);
                continue;
            }

        
       }
        din.close();
    }
    
    public void writeCSV(File output) throws IOException{
        FileWriter fw = new FileWriter(new File(output, "indexes.csv"));
        BufferedWriter bw = new BufferedWriter(fw);
        CSVPrinter csv = new CSVPrinter(bw, PMPDB.CSV_FORMAT.withHeader("Index", "Original Indexes", "type", "Image Path"));

        for(int i=0; i<entries; i++){
            Long originalIndex = originalIndexes.get(i);
            String name = names.get(i);
            final int type;
            if(indexes.get(i).compareTo(folderIndex)!=0){ // not a folder
                type = 0;
                String folderName = names.get(indexes.get(i).intValue());
                name = folderName + name;
            }else{ // folder
                type = name.equals("") ? 2 : 1;
            }
            csv.printRecord(i, originalIndex, type, name);
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
