import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;

import org.apache.commons.cli.Option;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;


public class PMPDB {
	private static final String APPTITLE = "PMPDB";

	private static final String HELP = "read all the PMP files containing the Picasa Database and the file " +
	                		"containing indexes thumbindex.db. \nThis program will create 3 csv files: albumdata.csv " +
	                		"(album database), catdata.csv (category database), imagedata.csv (picture database).\n\nParameters:\n";
	
	HashMap<String, ArrayList<String>> catdata;
	HashMap<String, ArrayList<String>> albumdata;
	HashMap<String, ArrayList<String>> imagedata;
	File folder;
	Indexes indexes;

    static final CSVFormat CSV_FORMAT = CSVFormat.EXCEL.withDelimiter(';');

	public PMPDB(File folder) {
		this.folder = folder;
		indexes = new Indexes(folder);
	}
	
	public void populate() throws Exception{
		indexes.Populate();
		catdata = getTable("catdata");
		albumdata = getTable("albumdata");
		imagedata = getTable("imagedata");
		ArrayList<String> is = new ArrayList<String>();
		for(Long l:indexes.indexes){
			is.add(l.toString());
		}
		ArrayList<String> ois = new ArrayList<String>();
		for(Long l:indexes.originalIndexes){
			ois.add(l.toString());
		}
		imagedata.put("index",is);
		imagedata.put("original index", ois);
		imagedata.put("fullname", indexes.fullnames);
	}
	
	
	private HashMap<String, ArrayList<String>>  getTable(final String table) throws Exception{
        //filter on table_*.pmp
        FilenameFilter filter = new FilenameFilter() { 
            @Override
            public boolean accept(File dir, String filename)
            { 
            	return filename.startsWith(table+"_") && filename.endsWith(".pmp");
            }
        };

        File[] files = folder.listFiles(filter);
        HashMap<String, ArrayList<String>> data = new HashMap<String, ArrayList<String>>();
        
        for(int i=0; i<files.length; i++){
            String filename 	= files[i].getName();
		    String fieldname	= filename.replace(table+"_", "").replace(".pmp", "");
	            
		    //System.out.print(fieldname+" ");
            data.put(fieldname, readColumn(new File(folder, filename))); //saving column fieldname
        }
        
        
        return data;
    }
	
	private static ArrayList<String> readColumn(File file) throws Exception{
		ArrayList<String> l = new ArrayList<String>();
        DataInputStream din = new DataInputStream
            (new BufferedInputStream
             (new FileInputStream(file)));
        long magic = ReadFunctions.readUnsignedInt(din); //file start with a magic sequence
        int type = ReadFunctions.readUnsignedShort(din); // then the entries type
         if ((magic=ReadFunctions.readUnsignedShort(din)) != 0x1332) {  //first constant
            throw new IOException("Failed magic2 "+Long.toString(magic,16));
        }
        if ((magic=ReadFunctions.readUnsignedInt(din)) != 0x2) {  //second constant
            throw new IOException("Failed magic3 "+Long.toString(magic,16));
        }
        if ((magic=ReadFunctions.readUnsignedShort(din)) != type) { // type again
            throw new IOException("Failed repeat type "+
                                  Long.toString(magic,16));
        }
        if ((magic=ReadFunctions.readUnsignedShort(din)) != 0x1332) {  //third constant
            throw new IOException("Failed magic4 "+Long.toString(magic,16));
        }
        long v = ReadFunctions.readUnsignedInt(din); //number of entries
        
        
        // records.
        for(int i=0; i<v; i++){
            if (type == 0) {
                l.add(ReadFunctions.dumpStringField(din));
            }
            else if (type == 0x1) {
                l.add(ReadFunctions.dump4byteField(din));
            }
            else if (type == 0x2) {
                l.add(ReadFunctions.dumpDateField(din));
            }
            else if (type == 0x3) {
                l.add(ReadFunctions.dumpByteField(din));
            }
            else if (type == 0x4) {
                l.add(ReadFunctions.dump8byteField(din));
            }
            else if (type == 0x5) {
                l.add(ReadFunctions.dump2byteField(din));
            }
            else if (type == 0x6) {
                l.add(ReadFunctions.dumpStringField(din));
            }
            else if (type == 0x7) {
                l.add(ReadFunctions.dump4byteField(din));
            }
            else {
                throw new IOException("Unknown type: "+Integer.toString(type,16));
            }
        }
        din.close();
        return l;
    }
	
	public void writeCSVs(File output, List<String> imagefields) throws Exception{
		writeCSV("catdata", catdata, output);
		writeCSV("imagedata", imagedata, output, imagefields);
		writeCSV("albumdata", albumdata, output);
	}

    private static void writeCSV(String table, HashMap<String, ArrayList<String>> data, File output) throws Exception {
        writeCSV(table, data, output, new ArrayList<String>(data.keySet()));
    }

    private static void writeCSV(String table, HashMap<String, ArrayList<String>> data, File output, List<String> keys) throws Exception{
    	// not all files have the same number of elements, get the maximum size for a table
        int max = 0;

        Iterator<String> it = keys.iterator();
        while (it.hasNext()) {
            String key = it.next();
            ArrayList<String> column = data.get(key);

            if (column != null) {
                max = Math.max(max, column.size());
            } else {
                it.remove();
            }
        }

        FileWriter fw = new FileWriter(new File(output, table+".csv"));
        BufferedWriter bw = new BufferedWriter(fw);
        CSVPrinter csv = new CSVPrinter(bw, CSV_FORMAT);

        // column names
        csv.printRecord(keys);

        for(int i=0; i<max ; i++){
            for (String key: keys){
                // for column that have less elements that the max, leave the trailing cells empty
                if(data.get(key).size()>i){
                    csv.print(data.get(key).get(i));
                } else {
                    csv.print("");
                }
            }
            csv.println();
        }
        csv.close();
    }
	
	@SuppressWarnings("static-access")
	public static void main(String[] args) throws Exception {
        EnvironmentVariables.StandardArguments a = EnvironmentVariables.parseCommandLine(APPTITLE, HELP, args,
                Option.builder("imagefields")
                        .hasArg()
                        .argName("field1[,field2[,...]]")
                        .desc("only include the given fields in imagedata.csv")
                        .build());

        String[] imagefields = {};
        String p = a.line.getOptionValue("imagefields");
        if (p != null && !p.isEmpty()) {
            imagefields = p.split(",");
        }

        PMPDB db = new PMPDB(a.folder);
        db.populate();
        dumpInfo("Album data", db.albumdata);
        dumpInfo("Cat data", db.catdata);
        dumpInfo("Image data", db.imagedata);
        db.writeCSVs(a.output, Arrays.asList(imagefields));
	}

	private static void dumpInfo(String title, HashMap<String, ArrayList<String>> data) {
		System.out.println("====================");
		System.out.println(title);
		for(Map.Entry<String, ArrayList<String>> e : data.entrySet()) {
			System.out.println("\t" + e.getKey() + ": " + e.getValue().size());
		}		
	}
}
