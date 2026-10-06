package com.dailyoffice.mei

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { DailyOfficeHome() } }
    }
}

@Composable
fun DailyOfficeHome() {
    Scaffold(
        topBar={ TopAppBar(title={Text("DailyOffice • Contador MEI")}) },
        floatingActionButton={ ExtendedFloatingActionButton(onClick={}, text={Text("Fotografar comprovante")}) }
    ){ pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Visão geral", style=MaterialTheme.typography.headlineSmall)
            Card { Column(Modifier.padding(16.dp)){ Text("Empresa x pessoal"); Text("Classifique cada comprovante e mantenha a foto original arquivada.") } }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                AssistChip(onClick={}, label={Text("Pendentes")})
                AssistChip(onClick={}, label={Text("A revisar")})
                AssistChip(onClick={}, label={Text("Relatório MEI")})
            }
            Text("Próxima etapa: câmera + OCR, revisão dos campos, dashboard mensal, débitos e exportação.")
        }
    }
}
