/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.fml;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.neoforged.neoforgespi.language.IConfigurable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class VyneConfigurable implements IConfigurable {
    private final UnmodifiableConfig config;

    public VyneConfigurable(UnmodifiableConfig config) {
        this.config = config;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getConfigElement(String... key) {
        if (config == null) return Optional.empty();
        Object value = config.get(Arrays.asList(key));
        if (value instanceof UnmodifiableConfig nested) return Optional.of((T) new VyneConfigurable(nested));
        return Optional.ofNullable((T) value);
    }

    @Override
    public List<? extends IConfigurable> getConfigList(String... key) {
        if (config == null) return List.of();
        Object value = config.get(Arrays.asList(key));
        if (!(value instanceof List<?> list)) return List.of();
        List<VyneConfigurable> out = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof UnmodifiableConfig nested) out.add(new VyneConfigurable(nested));
        }
        return out;
    }
}
