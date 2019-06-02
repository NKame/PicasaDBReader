import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FilenameUtils;


public class PicasaFaces {

	public static final String HELP = "PicasaFaces will extract the face information from the Picasa Database and save it in a csv file. " +
			"If the command line contains the argument '-convert' followed by the path to convert, " +
			"then imagemagick will create all the face thumbshots (in the output folder with a folder for each person). " +
			"A string replacement of the image paths can be done if the pictures location is different from the database.";
	PMPDB db;
	HashMap<String, String> personsId;
	HashMap<String, ArrayList<Face>> personFaces;
	HashMap<Long, Image> images;

	public PicasaFaces(File folder) {
		db = new PMPDB(folder);
		personsId = new HashMap<String, String>();
		personFaces = new HashMap<String, ArrayList<Face>>();
		images = new HashMap<Long, Image>();
	}
	
	public void populate() throws Exception{
		db.populate();
	}
	
	public void populatePersons(){
		ArrayList<String> tokens = db.albumdata.get("token");
		ArrayList<String> name = db.albumdata.get("name");
		int nb = tokens.size();
		personsId.put("0", "nobody");
		
		for (int i=0; i<nb; i++){
			String t = tokens.get(i);
			if(t.startsWith("]facealbum:")){
				personsId.put(t.split(":")[1], name.get(i));
			}
		}
	}
	
	@SuppressWarnings("static-access")
	public static void main(String[] args) throws Exception {
		EnvironmentVariables.StandardArguments a = EnvironmentVariables.parseCommandLine("PicasaFaces", HELP, args,
				OptionBuilder.withArgName("replaceRegex").hasArg().withDescription("regex to change original image path if needed").create("replaceRegex"),
    			OptionBuilder.withArgName("replacement").hasArg().withDescription("replacement for the regex").create("replacement"),
    			new Option("prefix", "add prefix to generated face images"),
    			OptionBuilder.withArgName("convert").hasArg().withDescription("path of convert from imagemagick (including convert itself)").create("convert"));
		CommandLine line = a.line;
		String regex=null;
		String replacement=null;
		boolean prefix =false;
		String convert = null;


		if(line.hasOption("replaceRegex") && !line.hasOption("replacement")){
			throw new Exception("both 'replaceRegex' and 'replacement' must be present");
		}
		if(!line.hasOption("replaceRegex") && line.hasOption("replacement")){
			throw new Exception("both 'replaceRegex' and 'replacement' must be present");
		}
		if(line.hasOption("replaceRegex") && line.hasOption("replacement")){
			regex = line.getOptionValue("replaceRegex");
			replacement = line.getOptionValue("replacement");
		}
		if(line.hasOption("prefix")){
			prefix=true;
		}
		if(line.hasOption("convert")){
			convert=EnvironmentVariables.expandEnvVars(line.getOptionValue("convert"));
		}

        PicasaFaces faces = new PicasaFaces(a.folder);
        faces.populate();
        faces.populatePersons();
        faces.gatherImages();
        faces.processImages(regex, replacement, a.output, prefix, convert);
	}

	public void gatherImages(){
		final ArrayList<String> facerects = db.imagedata.get("facerect");
		final ArrayList<String> personalbumids = db.imagedata.get("personalbumid");
		final ArrayList<String> widths = db.imagedata.get("width");
		final ArrayList<String> heights = db.imagedata.get("height");

		long nb=Math.min(db.indexes.entries, personalbumids.size());
		
		
		for(int i=0; i<nb; i++){
			Long originalIndex = db.indexes.originalIndexes.get(i);
			Indexes.Type type = db.indexes.types.get(i);

			String facerect = facerects.get(i);

			String personalbumid = personalbumids.get(i);
			String personName = personsId.get(personalbumid);


			if (type == Indexes.Type.FILE) {
				String path = db.indexes.fullnames.get(i);

				int w = Integer.parseInt(widths.get(i));
				int h = Integer.parseInt(heights.get(i));
				Image img = new Image(path, i, w, h);
	            if(!facerect.equals("0000000000000001")){
	            	img.hasFaceData=true;

	            	Face f = img.addFace(facerect, personName );
	            	if(!personalbumid.equals("0")){
	            		if(!personFaces.containsKey(personName)){
	            			personFaces.put(personName, new ArrayList<Face>());
	            		}

	            		personFaces.get(personName).add(f);
	            	}
	            }
				images.put((long)i, img);
			} else if (type == Indexes.Type.REFERENCE) {
            	if(!facerect.equals("0000000000000001")){
            		images.get(originalIndex).hasChild=true;
    	            Face f = images.get(originalIndex).addFace(facerect, personName);
    	            if(!personalbumid.equals("0")){
    	            	if(!personFaces.containsKey(personName)){
    	            		personFaces.put(personName, new ArrayList<Face>());
    	            	}

    	            	personFaces.get(personName).add(f);
    	            }
                }
			}// else folder
		}
	}

	private static final CSVFormat CSV_FORMAT = PMPDB.CSV_FORMAT.withHeader(
			"person", "prefix", "filename", "original image path", "transformed image path",
			"image width", "image height", "face x", "face y", "face width", "face height");

	public void processImages(String regex, String replacement, File output, boolean prefix, String convert) throws IOException, InterruptedException {
		FileWriter fw = new FileWriter(new File(output, "faces.csv"));
		BufferedWriter bw = new BufferedWriter(fw);
		CSVPrinter csv = new CSVPrinter(bw, CSV_FORMAT);

		for(String person:personFaces.keySet()){
			File folderPerson = new File(output, person);
			if(convert!=null && !folderPerson.mkdirs() && !folderPerson.isDirectory()) {
				throw new IOException("Could not create directory " + folderPerson);
			}
			
			int i=0;
			for(Face f:personFaces.get(person)){
				String path;
				path=FilenameUtils.separatorsToSystem(f.img.path);
				if(regex!=null && replacement!=null){
					path = path.replaceAll(regex, replacement);
				}
				String separator = File.separator;
				if(separator.equals("\\")){
					separator="\\\\";
				}
				String [] file = path.split(separator);
				String prefixStr = "";
				if(prefix){
					prefixStr = ""+ i +"_";
				}
				String convertedFileBasename = file[file.length - 1];
				File filename = new File(folderPerson, prefixStr+ convertedFileBasename);
				if(convert!=null && filename.exists()){
					System.out.println("Warning, the filename already exist: " + filename);
				}
				csv.printRecord(person, prefixStr, convertedFileBasename, f.img.path, path,
					f.img.w, f.img.h, f.x, f.y, f.w, f.h);

				if(convert!=null){
					String escapedFilename = filename.toString();
					if(File.separator.equals("\\")){
						path = "\""+path+"\"";
						escapedFilename = "\""+filename+"\"";
					}
					String []cmd = {convert,path, "-crop", f.w+"x"+f.h+"+"+ f.x +"+"+ f.y, escapedFilename};
					Process p = Runtime.getRuntime().exec(cmd);
					p.waitFor();
				}
				i++;
			}
		}
		csv.close();
	}
}
