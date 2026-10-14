"""Check class definitions in the built APK, including multidex files."""
import struct
import sys
import zipfile

required = {
    'Lorg/aspectj/runtime/reflect/Factory;',
    'Lorg/aspectj/lang/JoinPoint;',
    'Landroidx/room/RoomDatabase;',
    'Landroidx/room/rxjava3/RxRoom;',
    'Lnet/sqlcipher/database/SQLiteDatabase;',
    'Lio/reactivex/Observable;',
    'Lxcrash/XCrash;',
}
found = set()
with zipfile.ZipFile(sys.argv[1]) as apk:
    for name in apk.namelist():
        if not (name.startswith('classes') and name.endswith('.dex')):
            continue
        dex = apk.read(name)
        string_count, string_offset = struct.unpack_from('<II', dex, 0x38)
        type_count, type_offset = struct.unpack_from('<II', dex, 0x40)
        class_count, class_offset = struct.unpack_from('<II', dex, 0x60)
        for index in range(class_count):
            type_index = struct.unpack_from('<I', dex, class_offset + index * 32)[0]
            string_index = struct.unpack_from('<I', dex, type_offset + type_index * 4)[0]
            offset = struct.unpack_from('<I', dex, string_offset + string_index * 4)[0]
            while dex[offset] & 0x80:
                offset += 1
            offset += 1
            descriptor = dex[offset:dex.index(b'\0', offset)].decode('utf-8', errors='replace')
            if descriptor in required:
                found.add(descriptor)
                print(f'{name}: {descriptor}')
missing = required - found
if missing:
    raise SystemExit(f'Missing runtime classes in APK: {sorted(missing)}')
print('DJI AspectJ runtime classes are defined in the APK.')
