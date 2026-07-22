package com.subtlesight.observability;

import com.subtlesight.domain.Hashing;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class BackupService {
    private final DataSource dataSource;private final Path dataDir;private final ObjectMapper json;private final Clock clock;
    public BackupService(DataSource dataSource,Path dataDir,ObjectMapper json,Clock clock){this.dataSource=dataSource;this.dataDir=dataDir;this.json=json;this.clock=clock;}
    public Path create(Path target){try{Files.createDirectories(target.getParent());Path snapshot=Files.createTempFile(target.getParent(),"subtlesight-db-",".sqlite");try(Connection c=dataSource.getConnection();Statement s=c.createStatement()){String escaped=snapshot.toAbsolutePath().toString().replace("'","''");s.execute("VACUUM INTO '"+escaped+"'");}Map<String,String> manifest=new LinkedHashMap<>();try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(target))){add(zip,snapshot,"subtlesight.db",manifest);Path blobs=dataDir.resolve("blobs");Path blobTmp=blobs.resolve("tmp").toAbsolutePath().normalize();if(Files.exists(blobs))try(var paths=Files.walk(blobs)){for(Path p:paths.filter(Files::isRegularFile).filter(p->!p.toAbsolutePath().normalize().startsWith(blobTmp)).toList())add(zip,p,"blobs/"+blobs.relativize(p).toString().replace('\\','/'),manifest);}Map<String,Object> meta=Map.of("format","subtlesight-insightpack-v1","createdAt",clock.instant().toString(),"files",manifest,"secretsIncluded",false);byte[] bytes=json.writeValueAsBytes(meta);zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(bytes);zip.closeEntry();}Files.deleteIfExists(snapshot);return target;}catch(Exception e){throw new IllegalStateException("backup failed",e);}}
    public Path restore(Path pack,Path targetDataDir){try{Path target=targetDataDir.toAbsolutePath().normalize();Files.createDirectories(target);try(ZipFile zip=new ZipFile(pack.toFile())){ZipEntry manifestEntry=zip.getEntry("manifest.json");if(manifestEntry==null)throw new IllegalArgumentException("backup manifest is missing");@SuppressWarnings("unchecked")Map<String,Object> manifest=json.readValue(zip.getInputStream(manifestEntry),Map.class);if(!"subtlesight-insightpack-v1".equals(manifest.get("format"))||Boolean.TRUE.equals(manifest.get("secretsIncluded")))throw new SecurityException("unsupported or unsafe backup format");@SuppressWarnings("unchecked")Map<String,String> files=(Map<String,String>)manifest.get("files");for(Map.Entry<String,String> item:files.entrySet()){ZipEntry entry=zip.getEntry(item.getKey());if(entry==null)throw new IllegalArgumentException("backup entry missing: "+item.getKey());Path output=target.resolve(item.getKey()).normalize();if(!output.startsWith(target))throw new SecurityException("zip path traversal blocked");Files.createDirectories(output.getParent());try(InputStream input=zip.getInputStream(entry)){Files.copy(input,output,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}String actual=Hashing.sha256(Files.readAllBytes(output));if(!actual.equals(item.getValue()))throw new SecurityException("backup checksum mismatch: "+item.getKey());}}return target;}catch(Exception e){throw new IllegalStateException("restore failed",e);}}
    private void add(ZipOutputStream zip,Path file,String name,Map<String,String> manifest)throws IOException{byte[] bytes=Files.readAllBytes(file);manifest.put(name,Hashing.sha256(bytes));zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
}
