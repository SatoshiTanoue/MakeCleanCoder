package MakeCleanCoder;
import CommentFilter.CommentFilter;
import Dictionary.CommentDictionary;
import Parser.CleanCoderParser;
import Parser.ParseException;
import ResultData.CommentWithLineNumber;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author SatoshiTanoue
 * @version 1.0
 */
public class Controller implements Initializable {

    @FXML
    private AnchorPane root;

    @FXML
    private TextArea editArea;
    @FXML
    private VBox consoleAreaVbox;
    @FXML
    private VBox allCommentsAreaVbox;
    @FXML
    private TextArea lineNumberArea;
    @FXML
    private TextArea parseResultArea;
    @FXML
    private Label fileLabel;

    private String crlf = System.getProperty("line.separator");
    private File initialFile = new File(System.getProperty("user.home"));
    private File openedFileOnEditArea;

    //encodingがtrueのときはutf-8になる

    @FXML
    private boolean encoding = true;

    @FXML
    private void handleOnPreference(ActionEvent event){
    }

    @FXML
    private void handleOnUTF8(ActionEvent event){
        encoding = true;
        openFile(openedFileOnEditArea);
    }
    @FXML
    private void handleOnShiftJIS(ActionEvent event){
        encoding = false;
        openFile(openedFileOnEditArea);
    }

    @FXML
    private void handleOnFileOpen(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        fileChooser.setInitialDirectory(initialFile);
        File selectedFile = fileChooser.showOpenDialog(root.getScene().getWindow());
        initialFile = new File(selectedFile.getParent());
        fileLabel.setText(selectedFile.getName());
        openFile(selectedFile);
    }

    @FXML
    private void handleOnFileSave(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File");
        File selectedFile = fileChooser.showSaveDialog(root.getScene().getWindow());
        try (FileWriter filewriter = new FileWriter(selectedFile)) {
            filewriter.write(editArea.getText());
        } catch (IOException e) {
            System.out.println(e);
        }
    }
    //editAreaの中身から，コメントを解析する．
    @FXML
    private void handleOnExecuteParseComment(ActionEvent event)
    {
        String editAreaText = editArea.getText();
        ArrayList<String> improperCommentStringList = commentParse(editAreaText);
        ArrayList<Hyperlink> improperCommentLinkList =toHyperLinkList(improperCommentStringList);
        ArrayList<String> allCommentStringList = getAllComment(editAreaText);
        ArrayList<Hyperlink> allCommentLinkList = toHyperLinkList(allCommentStringList);
        //TODO　ファイルがないときに，検出できない．
        if(openedFileOnEditArea !=null){
            exportResultToFile(improperCommentStringList, openedFileOnEditArea.getName());
            exportResultsOfAllCommentsToFile(allCommentStringList,openedFileOnEditArea.getName());
        }


        consoleAreaVbox.getChildren().clear();
        TextFlow improperCommentCount= new TextFlow(new Text("検出したコメントの数 : "+String.valueOf(improperCommentLinkList.size())));
        consoleAreaVbox.getChildren().add(improperCommentCount);
        for(Hyperlink link : improperCommentLinkList){
            link.setOnAction(new EventHandler<ActionEvent>() {
                public void handle(ActionEvent e) {
                    int lineNumber = getLineNumberFromLink(link);
                    setScrollBar(lineNumber);
                }
            });
            TextFlow textFlow = new TextFlow(link);
            consoleAreaVbox.getChildren().add(textFlow);
        }
        allCommentsAreaVbox.getChildren().clear();

        TextFlow commentCount= new TextFlow(new Text("コメントの数:"+String.valueOf(allCommentLinkList.size())));
        allCommentsAreaVbox.getChildren().add(commentCount);
        for(Hyperlink link : allCommentLinkList){
            link.setOnAction(e -> {
                int lineNumber = getLineNumberFromLink(link);
                setScrollBar(lineNumber);
            });
            TextFlow textFlow = new TextFlow(link);
            allCommentsAreaVbox.getChildren().add(textFlow);
        }
    }
    //フォルダー内のコメントを解析
    @FXML
    private void handleOnAnalaysisCommentInFolder(ActionEvent event){
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File selectedFolder = directoryChooser.showDialog(root.getScene().getWindow());
        fileLabel.setText(selectedFolder.getName());
    }
    //複数ファイルのコメントを解析
    @FXML
    private void handleOnAnalaysisCommentInMultipleFiles (ActionEvent event) {

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        fileChooser.setInitialDirectory(initialFile);
        List<File> selectedFile = fileChooser.showOpenMultipleDialog(root.getScene().getWindow());
        initialFile = new File(selectedFile.get(0).getParent());
        String inputString="";
        ArrayList<String> improperCommentsStringList = new ArrayList<>();
        ArrayList<Hyperlink> improperCommentsHyperlinkList = new ArrayList<>();
        ArrayList<String> allCommentsStringList = new ArrayList<>();
        ArrayList<Hyperlink> allCommentsHyperLinkList = new ArrayList<>();

        consoleAreaVbox.getChildren().clear();
        allCommentsAreaVbox.getChildren().clear();
        try {
            for(File file: selectedFile){
                //選択された一つ目のファイルを開く
                openFile(file);
                BufferedReader br = encoding ? new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8")) : new BufferedReader(new InputStreamReader(new FileInputStream(file),"SJIS"));
                String str;
                inputString = "";
                while ((str = br.readLine()) != null) {
                    inputString += str + crlf;
                }
                String filePath = file.getPath();

                //コメントを解析した結果は，hyperlinkのlistで返ってくる．
                improperCommentsHyperlinkList.clear();
                improperCommentsStringList = commentParse(inputString);

                //Fileに出力する．
                exportResultToFile(improperCommentsStringList,file.getName());
                exportResultsOfAllCommentsToFile(allCommentsStringList,file.getName());

                improperCommentsHyperlinkList = toHyperLinkList(improperCommentsStringList);
                Text improperCommentCount = new Text("検出したコメントの数:"+String.valueOf(improperCommentsHyperlinkList.size()));
                TextFlow textFlow = new TextFlow(new Text("ファイル名"+file.getPath()+"  "),improperCommentCount);
                consoleAreaVbox.getChildren().add(textFlow);
                improperCommentsHyperlinkList.add(new Hyperlink());
                fileOpenAndSetTextAreaOfimproperComments(improperCommentsHyperlinkList,file);

                allCommentsStringList = getAllComment(inputString);
                allCommentsHyperLinkList = toHyperLinkList(allCommentsStringList);

                Text allCommentsCount = new Text("コメントの数:"+String.valueOf(allCommentsHyperLinkList.size()));
                TextFlow textFlow2 = new TextFlow(new Text("ファイル名"+file.getPath()+"  "),allCommentsCount);
                allCommentsAreaVbox.getChildren().add(textFlow2);
                // //改行を入れるために，
                allCommentsHyperLinkList.add(new Hyperlink());
                fileOpenAndSetTextAreaOfAllComments(allCommentsHyperLinkList, file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fileOpenAndSetTextAreaOfAllComments(ArrayList<Hyperlink> commentsLinkList, final File file) {
        for(Hyperlink link : commentsLinkList){
            //ファイルを開いてテキストエディタにセットする機能の追加
            link.setOnAction(new EventHandler<ActionEvent>() {
                public void handle(ActionEvent e) {
                    openFile(file);
                    int lineNumber = getLineNumberFromLink(link);
                    setScrollBar(lineNumber);
                }
            });
            allCommentsAreaVbox.getChildren().add(link);
        }
    }


    private void fileOpenAndSetTextAreaOfimproperComments(ArrayList<Hyperlink> commentsLinkList, final File file) {
        for(Hyperlink link : commentsLinkList){
            //ファイルを開いてテキストエディタにセットする機能の追加
            link.setOnAction(new EventHandler<ActionEvent>() {
                public void handle(ActionEvent e) {
                    openFile(file);
                    int lineNumber = getLineNumberFromLink(link);
                    setScrollBar(lineNumber);
                }
            });
            consoleAreaVbox.getChildren().add(link);
        }
    }
    private int getLineNumberFromLink(Hyperlink link) {
        String str[] =link.toString().split(":",0);
        String str2[] = str[0].split("'",0);
        return Integer.parseInt(str2[1]);
    }

    //変数名を解析
    @FXML
    private void executeVariableParser(ActionEvent event) {
        String str = editArea.getText();
        String[] strs = str.split(crlf);
        String outPutString = "";
        List<String> result = new ArrayList<String>();
        int count = 1;
        for (int i = 0; i < strs.length; i++) {
            try {
                CleanCoderParser parser = new CleanCoderParser(new StringReader(strs[i]));
                result = parser.VariableDeclaration();
                for (int j = 0; j < result.size(); j++) {
                    if (!result.get(j).isEmpty()) {
                        outPutString += String.valueOf(count) + ":" + result.get(j) + crlf;
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            count++;
        }
        parseResultArea.setText(outPutString);
    }

    @FXML
    private void createLineNumber(KeyEvent event) {
        if ("ENTER".equals(event.getCode().toString())) {
            double scrollTop = editArea.getScrollTop();
            int lineNumber = editArea.getText().split(crlf,-1).length;
            String line = "";
            for (int i = 1; i <= lineNumber + 1; i++) {
                line += String.valueOf(i) + crlf;
            }
            lineNumberArea.setText(line);
            editArea.setScrollTop(scrollTop);
        }
    }

    @FXML
    private void clearConsoleArea(ActionEvent event) {
        consoleAreaVbox.getChildren().clear();
    }

    @FXML
    private void clearEditArea(ActionEvent event) {
        editArea.setText("");
        lineNumberArea.setText("1"+crlf);
    }
    @FXML
    private void handleOnOpenSupportingRegexStage(ActionEvent event) {
        MakeCleanCoder.showSupportingRegexStage();
    }
    private void setScrollBar(int lineNumber){
        double value = 20;
        if(lineNumber == 1){
            editArea.setScrollTop(0);
        }else{
            editArea.setScrollTop((lineNumber-1)*value);
        }

    }

    //指定されたパスに存在するファイルを開き，テキストエリアに出力する．
    private void openFile(File file){
        openedFileOnEditArea = new File(file.getPath());
        fileLabel.setText(file.getName());
        try {
            String inputAllString;
            try(BufferedReader br = encoding ? new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8")) : new BufferedReader(new InputStreamReader(new FileInputStream(file),"SJIS"))){
                String str;
                inputAllString = "";
                while ((str = br.readLine()) != null) {
                    inputAllString += str + crlf;
                }
            }
            editArea.setText(inputAllString);
            int lineNumber = inputAllString.split(crlf,-1).length;
            String line = "";
            for (int i = 1; i <= lineNumber; i++) {
                line += String.valueOf(i) + crlf;
            }
            lineNumberArea.setText(line);
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
        }

    }

    // 引数 解析するソースコード
    // 戻り値 コンソールエリアに出力する文字列
    private ArrayList<String> commentParse(String inputString)
    {
        //TODO もっといい名前にしたい．
        //TODO filterに通したコメントと通ささなかったコメントの総数が同じじゃないとまずい 改行の数は変わらないようにする．
        CommentFilter commentFilter = new CommentFilter(inputString);
        CommentWithLineNumber resultPassedCommentFilter= new CommentWithLineNumber(commentFilter.getTextPassedThroughFilter());
        CommentWithLineNumber result = new CommentWithLineNumber(inputString);
        ArrayList<String> outPutList = new ArrayList<String>();

        //全てのコメントを表示するまで繰り返す．
        for(int i = 0; i < result.getCommentSize();  i++) {
            ArrayList<String> comment = result.getMap().get(result.getKeyValue().get(i));
            ArrayList<String> commentPassedCommentFilter = resultPassedCommentFilter.getMap().get(resultPassedCommentFilter.getKeyValue().get(i));
            for(int j = 0; j < comment.size(); j++){
                CommentDictionary dictionary = new CommentDictionary();
                //適切なコメントかどうか判断する．
                if (dictionary.isInappropriateComment(comment.get(j))) {
                    outPutList.add(String.valueOf(result.getKeyValue().get(i)) + ":" +comment.get(j).replaceAll(crlf, "") + " 不適切なコメントの可能性があります．");

                }
            }
        }
        return outPutList;
    }
    //StringをHyperLinkのListに変更する．
    private ArrayList<Hyperlink> toHyperLinkList(List<String> stringList)
    {
        ArrayList<Hyperlink> outPutList = new ArrayList<>();
        for(String str:stringList){
            outPutList.add(new Hyperlink(str));
        }
        return outPutList;
    }

    // 引数　ソースコード
    // 戻り値 ソースコードに存在するすべてのコメント
    private ArrayList<String> getAllComment(String inputString)
    {
        CommentWithLineNumber result = new CommentWithLineNumber(inputString);
        ArrayList<String> outPutLink= new ArrayList<String>();

        for(int i = 0; i < result.getCommentSize();  i++) {
            ArrayList<String> comment = result.getMap().get(result.getKeyValue().get(i));
            for(int j = 0; j < comment.size(); j++){
                outPutLink.add(String.valueOf(result.getKeyValue().get(i)) + ":" +comment.get(j).replaceAll(crlf, ""));
            }
        }
        return outPutLink;
    }

    //listは解析結果
    //fileNameはファイル名
    //result_filename.txtに解析結果を出力する．
    private void exportResultToFile(List<String> list,String fileName)
    {
        //TODO 文字コードによって，出力する文字コードを変更する．
        Calendar c = Calendar.getInstance();
        //フォーマットパターンを指定して表示する
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String exportString = sdf.format(c.getTime())+crlf;
        exportString += "検出したコメントの数:"+String.valueOf(list.size())+crlf;
        for(String str:list){
            exportString+=str+crlf;
        }
        //TODO:Resultのフォルダが無いとき，エクセプションが発生している．エラー処理を書く．
        //出力先ファイルのFileオブジェクトを作成
        File file = new File("src/Result/ImproperComments/"+"result_"+fileName+".txt");
        if(!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.err.println("ファイルを作れませんでした");
            }
        }

        try {
            BufferedWriter bw = encoding? new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file,true),"UTF-8")) : new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file,true),"SJIS"));
            bw.write(exportString);
            bw.newLine();
            bw.close();
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void exportResultsOfAllCommentsToFile(List<String> list,String fileName)
    {
        String exportString ="";
        //TODO:Resultのフォルダが無いとき，エクセプションが発生している．エラー処理を書く．
        //出力先ファイルのFileオブジェクトを作成
        for(String str:list){
            exportString+=str+crlf;
        }
        File file = new File("src/Result/AllComments/"+"result_all_comments"+fileName+".txt");

        if(!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.err.println("ファイルを作れませんでした");
            }
        }
        try {
            BufferedWriter bw = encoding? new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file,true),"UTF-8")) : new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file,true),"SJIS"));
            bw.write(exportString);
            bw.newLine();
            bw.close();
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        editArea.scrollTopProperty().bindBidirectional(lineNumberArea.scrollTopProperty());
    }

}

