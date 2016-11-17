import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.cli.*;

public class EnvironmentVariables {
	private static final String DEFAULT_PICASA_DB_PATH = EnvironmentVariables.expandEnvVars("%LOCALAPPDATA%/Google/Picasa2/db3");

	private static final String PARAM_PICASA_DB_FOLDER = "folder";
	private static final String PARAM_OUTPUT_FOLDER = "output";

	public static String expandEnvVars(String text) {
	    Map<String, String> envMap = System.getenv();
	    for (Entry<String, String> entry : envMap.entrySet()) {
	        String key = entry.getKey();
	        String value = entry.getValue().replace('\\', '/');
	        text = text.replaceAll("\\%" + key + "\\%", value);
	    }
	    return text;
	}

	static class StandardArguments {
		public final CommandLine line;
		public final File folder;
		public final File output;

		private StandardArguments(CommandLine line, File folder, File output) {
			this.line = line;
			this.folder = folder;
			this.output = output;
		}
	}

	private static void showHelp(String cmdLineSyntax, String header, Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(cmdLineSyntax, header, options, "\n", true);
	}


	public static StandardArguments parseCommandLine(String cmdLineSyntax, String header, String[] args, Option... extraOptions) {
		Options options = EnvironmentVariables.getStandardOptions();
		for (Option option : extraOptions) {
			options.addOption(option);
		}

		CommandLineParser parser = new GnuParser();
		try {
			CommandLine line = parser.parse( options, args );

			if (line.hasOption("h")){
				showHelp(cmdLineSyntax, header, options);
				System.exit(1);
			}

			return new StandardArguments(line, EnvironmentVariables.getPicasaDBFolder(line), EnvironmentVariables.getOutputFolder(line));
		}
		catch( ParseException exp ) {
			System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
			showHelp(cmdLineSyntax, header, options);
			System.exit(1);
			return null;
		}
	}

	private static Options getStandardOptions() {
		Options options = new Options();
		options.addOption("h", "help", false, "prints this help message");
		options.addOption(Option.builder(PARAM_PICASA_DB_FOLDER).argName("srcFolder").hasArg().desc("Picasa DB folder. Default is " + EnvironmentVariables.DEFAULT_PICASA_DB_PATH).build());
		options.addOption(Option.builder(PARAM_OUTPUT_FOLDER).argName("outputFolder").hasArg().required().desc("output folder").build());
		return options;
	}

	private static File getOutputFolder(CommandLine line) throws ParseException {
		final File output = new File(EnvironmentVariables.expandEnvVars(line.getOptionValue(PARAM_OUTPUT_FOLDER)));
		if (!output.mkdirs() && !output.isDirectory()) {
			throw new ParseException("could not create output folder: " + output);
		}
		return output;
	}

	private static File getPicasaDBFolder(CommandLine line) throws ParseException {
		String folder;
		// under M$-Windows7 picasa db is usually at %LOCALAPPDATA%/Google/Picasa2/db3
		// that will be expaned to C:/Users/{UserName}/AppData/Local/Google/Picasa2/db3
		if(line.hasOption(PARAM_PICASA_DB_FOLDER)){
			folder = EnvironmentVariables.expandEnvVars(line.getOptionValue(PARAM_PICASA_DB_FOLDER));
		} else {
			folder = EnvironmentVariables.DEFAULT_PICASA_DB_PATH;
		}

		File file = new File(folder);
		if (!file.isDirectory()) {
			throw new ParseException("Source folder does not exist:"+folder);
		}
		return file;
	}


}
