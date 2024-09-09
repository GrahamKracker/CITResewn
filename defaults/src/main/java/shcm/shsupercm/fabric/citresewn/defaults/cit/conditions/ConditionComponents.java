package shcm.shsupercm.fabric.citresewn.defaults.cit.conditions;

import io.shcm.shsupercm.fabric.fletchingtable.api.Entrypoint;
/*?>=1.21 {?*/
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.ComponentType;
/*?}?*/
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import shcm.shsupercm.fabric.citresewn.CITResewn;
import shcm.shsupercm.fabric.citresewn.api.CITConditionContainer;
import shcm.shsupercm.fabric.citresewn.cit.CITCondition;
import shcm.shsupercm.fabric.citresewn.cit.CITContext;
import shcm.shsupercm.fabric.citresewn.cit.CITParsingException;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyGroup;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyKey;
import shcm.shsupercm.fabric.citresewn.pack.format.PropertyValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConditionComponents extends CITCondition {
    /*?>=1.21 {?*/@Entrypoint(CITConditionContainer.ENTRYPOINT)/*?}?*/
    public static final CITConditionContainer<ConditionComponents> CONTAINER = new CITConditionContainer<>(ConditionComponents.class, ConditionComponents::new,
            "components", "component", "nbt");

    private ComponentType<?> componentType;
    private String componentMetadata;
    private String matchValue;

    private ConditionNBT fallbackNBTCheck;

    private static final Map<String, String> NBTToComponent = new HashMap<>(){};

    private static long fileUpdateTime = 0;

    //<editor-fold desc="ReloadNBTToComponent">
    @SuppressWarnings("CallToPrintStackTrace")
    private static void ReloadNBTToComponent()
    {
        //update file waits at least 15 seconds before updating
        if (System.currentTimeMillis() < fileUpdateTime + 15000)
        {
            return;
        }
        CITResewn.info("Reloading NBTToComponent");

        fileUpdateTime = System.currentTimeMillis();

        NBTToComponent.clear();

        BufferedReader br = null;

        try {

            // create file object
            File file = Paths.get(FabricLoader.getInstance().getConfigDir().toString(), "citresewn", "nbttocomponents.properties").toFile();

            //create file if it doesn't exist
            if (!file.exists())
            {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return; // guaranteed to be empty
            }

            // create BufferedReader object from the File
            br = new BufferedReader(new FileReader(file));

            String line;

            // read file line by line
            while ((line = br.readLine()) != null) {

                // split the line by :
                String[] parts = line.split(",");

                // first part is name, second is number
                String key = parts[0].trim();
                String value = parts[1].trim();

                CITResewn.info("key: " + key);
                CITResewn.info("value: " + value);

                if (!key.isEmpty() && !value.isEmpty())
                    NBTToComponent.put(key, value);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {

            // Always close the BufferedReader
            if (br != null) {
                try {
                    br.close();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    //</editor-fold>

    @Override
    public void load(PropertyKey key, PropertyValue value, PropertyGroup properties) throws CITParsingException {
        ReloadNBTToComponent();
        String metadata = value.keyMetadata();
        if (key.path().equals("nbt")) {
            var nbtPath = NBTToComponent.keySet().stream().filter(metadata::startsWith).findFirst();
            if(metadata.startsWith("ExtraAttributes"))
            {
                metadata = "minecraft:custom_data" + metadata.substring("ExtraAttributes".length());
            }
            else if(nbtPath.isPresent()) {
                var component = NBTToComponent.get(nbtPath.get());
                metadata = component + metadata.substring(nbtPath.get().length());
            }
            else
                throw new CITParsingException("NBT condition: \"" + metadata + "\" is no longer supported", properties, value.position());
        }

        metadata = metadata.replace("~", "minecraft:");

        String componentId = metadata.split("\\.")[0];

        if ((this.componentType = Registries.DATA_COMPONENT_TYPE.get(Identifier.tryParse(componentId))) == null)
            throw new CITParsingException("Unknown component type \"" + componentId + "\"", properties, value.position());

        metadata = metadata.substring(componentId.length());
        if (metadata.startsWith("."))
            metadata = metadata.substring(1);
        this.componentMetadata = metadata;

        this.matchValue = value.value();

        this.fallbackNBTCheck = new ConditionNBT();
        String[] metadataNbtPath = metadata.split("\\.");
        if (metadataNbtPath.length == 1 && metadataNbtPath[0].isEmpty())
            metadataNbtPath = new String[0];
        this.fallbackNBTCheck.loadNbtCondition(value, properties, metadataNbtPath, this.matchValue);
    }

    @Override
    public boolean test(CITContext context) {
        /*?>=1.21 {?*/
        var stackComponent = context.stack.getComponents().get(this.componentType);

        if (stackComponent != null) {
            NbtElement fallbackComponentNBT = ((ComponentType<Object>) this.componentType).getCodec().encodeStart(NbtOps.INSTANCE, stackComponent).getOrThrow();

            return this.fallbackNBTCheck.testPath(fallbackComponentNBT, 0, context);
        }
        /*?}?*/
        return false;
    }
}
