package br.ufsc.ine5611;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class App{
    
    public static void main(String[] args) throws InterruptedException {
        try {
        	
            if(args.length == 2){
            	
                Path caminhoArquivoOriginal = Paths.get(args[1]);
                Path caminhoArquivoTemporario = Files.createTempFile("arquivo", ".txt");
                

                //Criando o caminho para os arquivos até a regiao da memoria compartilhada
                FileChannel arquivoOriginal = FileChannel.open(caminhoArquivoOriginal, new OpenOption[]{StandardOpenOption.READ, StandardOpenOption.WRITE});
                FileChannel arquivoTemporario = FileChannel.open(caminhoArquivoTemporario, new OpenOption[]{StandardOpenOption.READ, StandardOpenOption.WRITE});

               
                //Memória compartilhada
                MappedByteBuffer mapedTemp = arquivoTemporario.map(FileChannel.MapMode.READ_WRITE, 0, arquivoOriginal.size()+4+32);
                ByteBuffer originalBuffer = arquivoOriginal.map(FileChannel.MapMode.READ_WRITE, 0, arquivoOriginal.size());

                mapedTemp.putInt(0, (int)arquivoOriginal.size());

                
                //copia o que ta no arquivo original pra dentro da região de memória compartilhada
                originalBuffer.position(0);
                mapedTemp.position(4);
                while(originalBuffer.remaining()>0){
                    mapedTemp.put(originalBuffer.get());
                }

                //cria o processo pai e inicia o signer
                ProcessBuilder processBuilderPai = new ProcessBuilder().command(args[0]);
                Process pai = processBuilderPai.start();


                
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(pai.getOutputStream());
                outputStreamWriter.write("SIGN "+ caminhoArquivoTemporario.toAbsolutePath()+"\n");
                outputStreamWriter.flush();

                pai.waitFor();
                
                
                

                //transformando um byte em uma string base64
                byte[] signature = new byte[32];
                mapedTemp.position((int)arquivoOriginal.size()+4);
                for(int i = 0;i<signature.length;i++){
                    signature[i] = mapedTemp.get();
                }
                
                System.out.println("Hash lido da memória\n"+Base64.getEncoder().encodeToString(signature));

                System.out.println("Hash calculado sem superfaturamento:\n"+Base64.getEncoder().encodeToString(getExpectedSignature(caminhoArquivoOriginal.toFile())));
            
                pai.destroy();
                
            } else{
                throw new IOException("Argumentos invalidos");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    

    //SHA-256 sem superfaturamento
    private static byte[] getExpectedSignature(File file){
        MessageDigest md = null;
        try{
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
        }
        
        try(FileInputStream in = new FileInputStream(file)){
            while(in.available()>0){
                md.update((byte) in.read());
            }
        } catch(IOException ex){
        }
        return md.digest();
    }
}
