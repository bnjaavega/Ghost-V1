package com.ghost.editor.data

/** Concepto de la biblioteca "Práctica". */
data class PracticeTopic(
    val name: String,
    val summary: String,
    val code: String,
    /** Salida esperada al ejecutar [code], o null si depende del usuario. */
    val output: String?,
    /** Nombre del archivo que se crea al pulsar "Probar en el editor". */
    val fileName: String
)

object PracticeContent {
    val topics: List<PracticeTopic> = listOf(
        PracticeTopic(
            name = "print()",
            summary = "Muestra texto o valores en la consola.",
            code = "print(\"Hola, mundo\")\nprint(\"Suma:\", 2 + 3)\n",
            output = "Hola, mundo\nSuma: 5",
            fileName = "print.py"
        ),
        PracticeTopic(
            name = "input()",
            summary = "Pide al usuario que escriba algo. Siempre devuelve texto (str). " +
                "En Ghost, la consola te deja escribir la respuesta cuando el programa la pide.",
            code = "nombre = input(\"¿Cómo te llamas? \")\nprint(\"Hola\", nombre)\n",
            output = "¿Cómo te llamas? Ana   ← lo que escribes\nHola Ana",
            fileName = "input.py"
        ),
        PracticeTopic(
            name = "int()",
            summary = "Convierte un valor a número entero. Si recibe un decimal, descarta la parte decimal.",
            code = "edad = int(\"15\")\nprint(edad + 1)\nprint(int(3.9))\n",
            output = "16\n3",
            fileName = "int.py"
        ),
        PracticeTopic(
            name = "float()",
            summary = "Convierte un valor a número decimal.",
            code = "precio = float(\"2.50\")\nprint(precio * 2)\n",
            output = "5.0",
            fileName = "float.py"
        ),
        PracticeTopic(
            name = "str()",
            summary = "Convierte un valor a texto, por ejemplo para unirlo con otras cadenas.",
            code = "n = 42\ntexto = \"Tengo \" + str(n) + \" años\"\nprint(texto)\n",
            output = "Tengo 42 años",
            fileName = "str.py"
        ),
        PracticeTopic(
            name = "Variables",
            summary = "Una variable es un nombre que guarda un valor. Se crea con el signo =.",
            code = "nombre = \"Luis\"\nedad = 20\naltura = 1.75\nprint(nombre, edad, altura)\n",
            output = "Luis 20 1.75",
            fileName = "variables.py"
        ),
        PracticeTopic(
            name = "if / else",
            summary = "Ejecuta un bloque u otro según se cumpla una condición. " +
                "El código del bloque va indentado (4 espacios).",
            code = "edad = 18\nif edad >= 18:\n    print(\"Eres mayor de edad\")\nelse:\n    print(\"Eres menor de edad\")\n",
            output = "Eres mayor de edad",
            fileName = "if_else.py"
        ),
        PracticeTopic(
            name = "for",
            summary = "Repite un bloque para cada elemento de una secuencia. " +
                "range(1, 4) genera 1, 2 y 3 (el final no se incluye).",
            code = "for i in range(1, 4):\n    print(\"Vuelta\", i)\n",
            output = "Vuelta 1\nVuelta 2\nVuelta 3",
            fileName = "for.py"
        ),
        PracticeTopic(
            name = "while",
            summary = "Repite un bloque mientras la condición sea verdadera. " +
                "Cuida que la condición llegue a ser falsa o el programa no terminará (puedes detenerlo con el botón de parar).",
            code = "contador = 3\nwhile contador > 0:\n    print(contador)\n    contador -= 1\nprint(\"¡Despegue!\")\n",
            output = "3\n2\n1\n¡Despegue!",
            fileName = "while.py"
        ),
        PracticeTopic(
            name = "def",
            summary = "Define una función: un bloque de código con nombre que puedes reutilizar. " +
                "return devuelve un resultado.",
            code = "def saludar(nombre):\n    return \"Hola \" + nombre\n\nprint(saludar(\"Ana\"))\n",
            output = "Hola Ana",
            fileName = "def.py"
        ),
        PracticeTopic(
            name = "Listas",
            summary = "Una lista guarda varios valores en orden. Se escribe entre corchetes " +
                "y el primer elemento está en la posición 0.",
            code = "frutas = [\"manzana\", \"pera\"]\nfrutas.append(\"uva\")\nprint(frutas)\nprint(len(frutas))\nprint(frutas[0])\n",
            output = "['manzana', 'pera', 'uva']\n3\nmanzana",
            fileName = "listas.py"
        ),
        PracticeTopic(
            name = "Operadores básicos",
            summary = "Suma +, resta -, multiplicación *, división /, división entera //, " +
                "resto % y potencia **.",
            code = "print(7 + 2)\nprint(7 - 2)\nprint(7 * 2)\nprint(7 / 2)\nprint(7 // 2)\nprint(7 % 2)\nprint(7 ** 2)\n",
            output = "9\n5\n14\n3.5\n3\n1\n49",
            fileName = "operadores.py"
        )
    )
}
