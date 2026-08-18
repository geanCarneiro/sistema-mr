/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.tool_calling;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 *
 * @author gean.carneiro
 */
@Component
public class PythonToolConfig {
    
    private final Logger logger = LoggerFactory.getLogger(PythonToolConfig.class);
    
    public record PythonRequest(String code) {}
    
    public record PythonResponse(String output, String error){}
    
    @Tool(description = "Executa scripts em Python 3 para cálculos complexos, análise de dados ou algoritmos. Retorna a saída do terminal (stdout/stderr).")
    public PythonResponse executePythonCode(PythonRequest request) {
        
        try {
            logger.info("Executando script python");
            logger.info(request.code());

            Path tempFile = Files.createTempFile("script__", ".py");


            // Injeta o wrapper com auto-install dinâmico
            String scriptComWrapper = """
                import sys
                import subprocess

                modulos_tentados = set()

                def execute():
                %s

                while True:
                    try:
                        execute()
                        break
                    except ModuleNotFoundError as e:
                        modulo = e.name
                        if modulo in modulos_tentados:
                            raise e

                        modulos_tentados.add(modulo)

                        res = subprocess.run(
                                    [sys.executable, "-m", "pip", "install", modulo],
                                    stdout=subprocess.DEVNULL,
                                    stderr=subprocess.DEVNULL
                                )
                        if res.returncode != 0:
                            raise e
                    except Exception as e:
                        raise e
                """.formatted(request.code().indent(4));




            Files.writeString(tempFile, scriptComWrapper);

            logger.info("Arquivo temporario criado");

            ProcessBuilder pb = new ProcessBuilder("python", tempFile.toAbsolutePath().toString());
            Process process = pb.start();

            logger.info("Script executado");

            BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = stdOut.readLine()) != null) {
                output.append(line).append("\n");
            }

            BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder error = new StringBuilder();
            while((line = stdErr.readLine()) != null) {
                error.append(line).append("\n");
            }

            process.waitFor();

            logger.info("Resultado: " + output);
            logger.info("Erro: " + error);

            Files.deleteIfExists(tempFile);

            logger.info("Arquivo temporario apagado, devolvendo o resultado/erro para o Gemini");

            return new PythonResponse(output.toString().trim(), error.toString().trim());
        } catch (Exception e) {
            logger.error("Erro ao executar script: " + e.getMessage(), e);
            return new PythonResponse("", "Erro ao executar script: " + e.getMessage());
        }
    }
    
    
}
