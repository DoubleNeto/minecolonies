package com.minecolonies.api.crafting;

import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.util.Log;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A {@link CraftingType} for the vanilla {@link IRecipeType}
 * @param <C> the crafting inventory type
 * @param <T> the recipe type
 */
public class RecipeCraftingType<C extends IInventory, T extends IRecipe<C>> extends CraftingType
{
    private final IRecipeType<T> recipeType;
    private final Predicate<IRecipe<C>> predicate;

    /**
     * Create a new instance
     * @param id the crafting type id
     * @param recipeType the vanilla recipe type
     * @param predicate filter acceptable recipes, or null to accept all
     */
    public RecipeCraftingType(@NotNull final ResourceLocation id,
                              @NotNull final IRecipeType<T> recipeType,
                              @Nullable final Predicate<IRecipe<C>> predicate)
    {
        super(id);
        this.recipeType = recipeType;
        this.predicate = predicate;
    }

    @Override
    @NotNull
    public List<IGenericRecipe> findRecipes(@NotNull RecipeManager recipeManager,
                                            @Nullable final World world)
    {
        final List<IGenericRecipe> recipes = new ArrayList<>();
        for (final IRecipe<C> recipe : recipeManager.byType(recipeType).values())
        {
            if (predicate != null && !predicate.test(recipe)) continue;

            tryAddingVanillaRecipe(recipes, recipe, world);
        }
        return recipes;
    }

    private static void tryAddingVanillaRecipe(@NotNull final List<IGenericRecipe> recipes,
                                               @NotNull final IRecipe<?> recipe,
                                               @Nullable final World world)
    {
        if (recipe.getResultItem().isEmpty()) return;     // invalid or special recipes
        try
        {
            final IGenericRecipe genericRecipe = GenericRecipe.of(recipe, world);
            if (genericRecipe == null || genericRecipe.getInputs().isEmpty()) return;
            recipes.add(genericRecipe);
        }
        catch (final Exception ex)
        {
            Log.getLogger().warn("Error evaluating recipe " + recipe.getId() + "; ignoring.", ex);
        }
    }
}
