import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//iText imports
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

public class ReadPdf {
	public static String DATE_TIME_PATTERN = "([1-9]|1[012])[- /.]([1-9]|[12][0-9]|3[01])[- /.]\\d\\d(\\s+)(1[012]|[1-9]):[0-5][0-9](\\s)?(?i)(am|pm)";
	
	public static String LINE_TYPE_NORMAL = "[A-Z][A-Z]+\\s/\\s[A-Z][A-Z]+.*$";
	public static String LINE_TYPE_1_PATTERN = DATE_TIME_PATTERN + "\\s\\d+\\s+\\d+$";
	public static String LINE_TYPE_2_PATTERN = DATE_TIME_PATTERN + "\\s[a-zA-Z]+$";
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		readPDF();
	}

	public static void readPDF() {
		try {
			PdfReader reader = new PdfReader("/home/hugo/Downloads/Flight Activity Summary_7Mar2015_0916412350.pdf");
			int nbOfPage = reader.getNumberOfPages();
			
			//Iterate over all the pdf pages
			for (int i = 1; i < nbOfPage; i++) {
				Path path = Paths.get("/home/hugo/tmp/outbox/pdfToCsv/page" + i + ".csv");
				BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);

				String page = PdfTextExtractor.getTextFromPage(reader, i);
				String[] lines = page.split("\\n");
				String previousLine = null;
				for (int x = 0; x < lines.length; x++) {
					String currentLine = lines[x];
					
					//Check if line has been correctly interpreted by Itext
					if(previousLine == null){
						//Check the current line (if line has not been correctly interpreted, let's say this is the first part of the line)
						if(currentLine.trim().startsWith("DOWNTIME")
								|| currentLine.trim().matches(LINE_TYPE_NORMAL) //ex:"10/15/12   7:00 pm;EIDW / DUBLIN ;10/15/12   8:05 pm;EGLC / LONDON ; 01+05 ; 258  ;  0"
								|| currentLine.trim().matches(LINE_TYPE_1_PATTERN) 
								|| currentLine.trim().matches(LINE_TYPE_2_PATTERN.substring(0, LINE_TYPE_2_PATTERN.length()-1) + "\\s+" + LINE_TYPE_1_PATTERN)){
							previousLine = lines[x];
							continue;
						}else if (currentLine.trim().matches(DATE_TIME_PATTERN + "\\s+" + DATE_TIME_PATTERN + "\\s\\d+\\s+\\d+$")){//ex: "12/20/12  12:01 am 12/20/12  11:59 pm 0  0"
							previousLine = insertAfterFirstDate(currentLine, lines[x+1]);
							continue;
						}
					}else{
						//We know now that the previous has not been correctly interpreted so following will be the second part of the line
						if(currentLine.matches(DATE_TIME_PATTERN)){//case 1 
							currentLine = currentLine + " " + previousLine;
							previousLine = null;	
						}else if(currentLine.trim().matches(LINE_TYPE_2_PATTERN)){//case 2
							currentLine = currentLine + " " + previousLine;
							previousLine = currentLine;
							
							continue;
						}else if(currentLine.trim().matches("[a-z]+\\s+-$")){//case 3 - (ex: "ron -" )
							//Insert current line into the previous line
							currentLine = insertAfterFirstDate(previousLine , currentLine);
							previousLine = null;
							
						}else{
							continue;
						}
					}
					
					currentLine = ReadPdf.splitString(currentLine);
					if (currentLine == null) {
						writer.write(lines[x] + "\n");
					} else {
						writer.write(currentLine + "\n");
					}
				}

				writer.close();
				/*
				 * System.out.println("Page Content:\n\n"+page+"\n\n");
				 * System.out.println("Is this document tampered: +reader.isTampered()); System.out.println(
				 * "Is this document encrypted: "+reader.isEncrypted());
				 */
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Insert the currentLine into the previous line.<br>
	 * 
	 * @param previousLine - the line to insert into
	 * @param currentLine - the data to insert
	 * @return the line in which currentLine has been inserted
	 */
	public static String insertAfterFirstDate(String previousLine, String currentLine){
		String s = new String();
		String [] splittedLine = previousLine.trim().split("\\s+");
		
		for (int j = 0; j < splittedLine.length; j++) {
			if(j == 3){
				s+=" " + currentLine;
			}
			s+=" " + splittedLine[j];
		}
		
		return s;
	}
	
	public static String splitString(String toSplit) {
		//Check if the given line is allow to be parsed
		if(!toSplit.trim().matches(DATE_TIME_PATTERN + "\\s+[a-zA-Z]+\\s+(/|-)\\s+[a-zA-Z]+\\s+" + DATE_TIME_PATTERN + ".*$")){
			return null;
		}
		
		//Continue processing
		String c1s = null;
		String c2s = null;
		String c3s = null;
		String c4s = null;
		String c5s = null;
		String c6s = null;
		String c7s = null;
		String[] allMatches = new String[2];

		//Search for column 1 and column 3
		Matcher date = Pattern.compile(DATE_TIME_PATTERN).matcher(toSplit);// date 1 + date2 pattern
		int count = 0;
		while (date.find()) {
			allMatches[count] = date.group();
			count++;
		}
		c1s = allMatches[0];
		c3s = allMatches[1];

		//Search for column 2 & column 4
		allMatches = new String[2];
		Matcher airport = Pattern.compile("[a-zA-Z]+\\s+\\W\\s+[a-zA-Z]+\\s").matcher(toSplit);// departure + arrival
		count = 0;
		while (airport.find()) {
			allMatches[count] = airport.group();
			count++;
		}
		c2s = allMatches[0];
		c4s = allMatches[1];

		//Search for column 5
		allMatches = new String[2];
		Matcher ete = Pattern.compile("(\\s+)\\d\\d\\+\\d\\d(\\s+)").matcher(toSplit); // ETE
		count = 0;
		while (ete.find()) {
			allMatches[count] = ete.group();
			count++;
		}
		c5s = allMatches[0];

		//Search for column 7
		allMatches = new String[2];
		Matcher pax = Pattern.compile("(\\s+)\\d+$").matcher(toSplit); // PAX
		count = 0;
		while (pax.find()) {
			allMatches[count] = pax.group();
			count++;
		}
		c7s = allMatches[0];

		//Search for column 6
		int begin = 0, offset = 0;
		if(c4s == null && c5s == null){//Case of empty column 4 & column 5 
			begin = toSplit.lastIndexOf(c3s.trim());
			offset+=c3s.trim().length();
		}else{
			begin = toSplit.indexOf(c5s.trim());
			offset+=c5s.trim().length();
		}
		int end = toSplit.trim().length() - c7s.trim().length();
		c6s = toSplit.trim().substring(begin+offset, end);
		
		//Final result
		String result = (c1s==null?"":c1s) 
				+ ";" + (c2s==null?"":c2s) 
				+ ";" + (c3s==null?"":c3s) 
				+ ";" + (c4s==null?"":c4s) 
				+ ";" + (c5s==null?"":c5s) 
				+ ";" + (c6s==null?"":c6s) 
				+ ";" + (c7s==null?"":c7s);

		return result;
	}

	public static String splitString2(/* String str */) {
		String str = "9/8/10   8:00 pm EGLF / FARNBOROUGH 9/9/10   6:00 am OOMS / MUSCAT 07+00 3,158  0";
		str = str.trim();

		if (str.length() < 54) {
			return null;
		}

		int c1 = -1;
		String c1s = null;

		int c2 = -1;
		String c2s = null;

		int c3 = -1;
		String c3s = null;

		int c4 = -1;
		String c4s = null;

		int c5 = -1;
		String c5s = null;

		int c6 = -1;
		String c6s = null;

		int c7 = -1;
		String c7s = null;

		// Searching for the 1st column
		c1 = str.indexOf("pm");
		if (c1 < 0 || c1 == str.lastIndexOf("pm")) {
			c1 = str.indexOf("am");

			if (c1 < 0) {
				// reject - malformed line
				// System.out.println("reject - malformed line");
				// System.exit(0);
				return null;
			}
		}

		// We have found the 1st column
		c1s = str.substring(0, c1 += 2).trim();

		// Searching for the 2nd column
		c2 = c3 = str.lastIndexOf("am");
		if (c2 < 0 || (c2 + 2) == c1) {
			c2 = c3 = str.lastIndexOf("pm");

			if (c2 < 0) {
				// reject - malformed line - unable to find the second date/time
				// System.out.println("reject - malformed line");
				// System.exit(0);
				return null;
			}
		}

		// Search for the first digit
		try {
			c2s = str.substring(c1, c2 += 2);// use c2s here as temporary var
		} catch (Exception e) {
			System.out.println(str);
		}
		c2 = -1;
		for (int i = 0; i < c2s.length() - 1; i++) {
			char c = c2s.charAt(i);

			if (Character.isDigit(c)) {
				c2 = c1 + i;
				break;
			}
		}

		if (c2 < 0) {
			c3 = -1;
			// reject - malformed line - cannot find digit !
			// System.out.println("reject - malformed line");
			// System.exit(0);
			return null;
		}

		// we have found the 2nd and the 3rd column
		c2s = str.substring(c1, c2).trim();
		c3s = str.substring(c2, c3 += 2).trim();

		// Searching for the 4th column
		// Search for the first digit
		c4s = str.substring(c3);// use c4s here as temporary var
		for (int i = 0; i < c4s.length() - 1; i++) {
			char c = c4s.charAt(i);

			if (Character.isDigit(c)) {
				c4 = c3 + i;
				break;
			}
		}

		if (c4 < 0) {
			// reject - malformed line - cannot find digit !
			// System.out.println("reject - malformed line");
			// System.exit(0);
			return null;
		}

		// we have found the 4th column
		c4s = str.substring(c3, c4).trim();

		// Searching for the 5th column - we have found the 4th column
		c5s = str.substring(c4).trim().substring(0, 6).trim();
		c5 = c4 + 6;

		// Searching for the 6th column - we have found the 6th column
		c6 = str.length() - 1;
		c6s = str.substring(c5, c6).trim();

		// Searching for the 6th column - we have found the 6th column
		c7s = str.substring(c6).trim();
		c7 = str.length();

		// Print found values
		System.out.println("c1-> " + c1s + ";");
		System.out.println("c2-> " + c2s + ";");
		System.out.println("c3-> " + c3s + ";");
		System.out.println("c4-> " + c4s + ";");
		System.out.println("c5-> " + c5s + ";");
		System.out.println("c6-> " + c6s + ";");
		System.out.println("c7> " + c7s);

		return c1s + ";" + c2s + ";" + c3s + ";" + c4s + ";" + c5s + ";" + c6s + ";" + c7s;
	}

	public static void digitSearch() {
		String s = "ffsdffsdf 2/9/10 fgsdg";

		for (int i = 0; i < s.length() - 1; i++) {
			char c = s.charAt(i);

			if (Character.isDigit(c)) {
				System.out.println(i);
				break;
			}
		}
	}

	public static void splitSetOfLine() {
		//String s = "9/8/10   8:00 pm EGLF / FARNBOROUGH 9/9/10   6:00 am OOMS / MUSCAT 07+00 3,158  0";
		String s = " EGLF / FARNBOROUGH 9/9/10   6:00 am OOMS / MUSCAT 07+00 3,158  0";
		//String s = "EGLF / FARNBOROUGH";

		//if(s.matches("[A-Z]+\\s\\W\\s[A-Z]+\\s*")){
		if(s.matches("[A-Z]+.*$")){
			System.out.println("OK");
		}else{
			System.out.println("NOK");
		}

	}

}
