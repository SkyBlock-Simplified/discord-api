package dev.sbs.discordapi;

import dev.sbs.api.SimplifiedApi;
import dev.sbs.api.data.model.Model;
import dev.sbs.api.util.data.tuple.Pair;
import dev.sbs.api.util.helper.DataUtil;
import dev.sbs.api.util.helper.SystemUtil;
import org.junit.jupiter.api.Test;

import javax.persistence.Table;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SqlToJsonDumpTest {

    private static final TestConfig testConfig;

    static {
        try {
            File currentDir = new File(SimplifiedApi.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            testConfig = new TestConfig(currentDir.getParentFile(), "testsql");
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to retrieve current directory", exception); // Should never get here
        }
    }

    @Test
    public void dumpDatabaseToJson_ok() {
        File currentDir = SystemUtil.getUserDir();
        File dbDir = new File(currentDir, "build/db");

        if (!dbDir.exists())
            dbDir.mkdirs();

        System.out.println("Connecting to database...");
        SimplifiedApi.getSessionManager().connectSql(testConfig);

        SimplifiedApi.getSessionManager()
            .getSession()
            .getModels()
            .stream()
            .map(modelClass -> (Class<? extends Model>) modelClass)
            .filter(modelClass -> modelClass.isAnnotationPresent(Table.class))
            .map(modelClass -> Pair.of(
                modelClass,
                modelClass.getAnnotation(Table.class).name()
            ))
            .forEach(entry -> {
                String tableName = entry.getValue();
                System.out.println("Saving " + tableName + "...");

                try (FileWriter fileWriter = DataUtil.newFileWriter(dbDir.getAbsolutePath() + "/" + tableName + ".json")) {
                    fileWriter.write(SimplifiedApi.getGson().toJson(SimplifiedApi.getRepositoryOf(entry.getKey()).findAll()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

}