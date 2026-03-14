# Example MSF Files

Sample `.msf` files for testing and reference.

| File | Description |
|---|---|
| `minimal.msf` | Single stone block, one layer, no entities. The smallest valid MSF file. |
| `multi_layer.msf` | 5×5×5 structure with three layers (foundation, walls, roof). Demonstrates semantic construction phases. |
| `with_entities.msf` | Small room with an armor stand and a chest with contents. Demonstrates entity and block entity support. |

## Generating examples

These files are generated from the CLI using conversion or from in-game extraction. To regenerate:

```bash
# Generate minimal.msf from the included NBT fixture
java -jar msf-cli.jar convert fixtures/minimal.nbt minimal.msf

# Or extract in-game
/msf extract 0 64 0 4 68 4 multi_layer
```
