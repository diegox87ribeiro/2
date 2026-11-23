package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.Ativo
import com.example.data.local.CarteiraLocalDao
import com.example.data.local.InvestDatabase
import com.example.data.local.Transacao
import com.example.data.repository.CarteiraLocalRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CarteiraLocalRoomRobolectricTest {

    private lateinit var db: InvestDatabase
    private lateinit var dao: CarteiraLocalDao
    private lateinit var repository: CarteiraLocalRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InvestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.carteiraLocalDao()
        repository = CarteiraLocalRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `inserir e observar ativos e transacoes com relacao 1 para N`() = runBlocking {
        val smre = Ativo(
            ticker = "SMRE11",
            nome = "Santander Multiestratégia FII",
            classe = "FII",
            setor = "Híbrido",
            quantidade = 100.0,
            precoMedio = 9.50,
            precoAtual = 9.80,
            possuiCotacaoMercado = true,
            dataComDia = 30,
            dataExDia = 3
        )
        repository.salvarAtivo(smre)

        dao.inserirTransacao(
            Transacao(
                ativoTicker = "SMRE11",
                tipo = "PROVENTO",
                quantidade = 100.0,
                precoUnitario = 0.11,
                valorTotal = 11.00,
                dataIso = "15/03/2026",
                observacoes = "Dividendo mensal FII"
            )
        )

        val ativos = repository.ativosFlow.first()
        assertEquals(1, ativos.size)
        assertEquals("SMRE11", ativos.first().ticker)
        assertEquals(30, ativos.first().dataComDia)
        assertEquals(3, ativos.first().dataExDia)

        val relacao = repository.observarAtivoComTransacoes("smre11").first()
        assertNotNull(relacao)
        assertEquals(1, relacao!!.transacoes.size)
        assertEquals("PROVENTO", relacao.transacoes.first().tipo)
        assertEquals(11.00, relacao.transacoes.first().valorTotal, 0.001)
    }

    @Test
    fun `registrarTransacaoEAtualizarAtivo recalcula preco medio e preserva em provento e venda`() = runBlocking {
        val base = Ativo(
            ticker = "HGLG11",
            nome = "Pátria Logística FII",
            classe = "FII",
            setor = "Logística",
            dataComDia = 30
        )

        // Compra 1: 10 cotas a R$ 150,00
        repository.registrarTransacao(
            ativoBase = base,
            transacao = Transacao(
                ativoTicker = "HGLG11",
                tipo = "COMPRA",
                quantidade = 10.0,
                precoUnitario = 150.0,
                valorTotal = 1500.0,
                dataIso = "10/01/2026"
            )
        )

        // Compra 2: 10 cotas a R$ 160,00 + R$ 10,00 taxas => PM = (1500 + 1600 + 10) / 20 = 155,50
        repository.registrarTransacao(
            ativoBase = base,
            transacao = Transacao(
                ativoTicker = "HGLG11",
                tipo = "COMPRA",
                quantidade = 10.0,
                precoUnitario = 160.0,
                valorTotal = 1600.0,
                taxas = 10.0,
                dataIso = "10/02/2026"
            )
        )

        // Provento: mantém 20 cotas e PM 155,50
        repository.registrarTransacao(
            ativoBase = base,
            transacao = Transacao(
                ativoTicker = "HGLG11",
                tipo = "PROVENTO",
                quantidade = 20.0,
                precoUnitario = 1.10,
                valorTotal = 22.0,
                dataIso = "14/02/2026"
            )
        )

        // Venda parcial: 5 cotas => restam 15 cotas, PM preservado em 155,50
        repository.registrarTransacao(
            ativoBase = base,
            transacao = Transacao(
                ativoTicker = "HGLG11",
                tipo = "VENDA",
                quantidade = 5.0,
                precoUnitario = 162.0,
                valorTotal = 810.0,
                dataIso = "20/02/2026"
            )
        )

        val comTransacoes = repository.observarAtivoComTransacoes("HGLG11").first()
        assertNotNull(comTransacoes)
        assertEquals(15.0, comTransacoes!!.ativo.quantidade, 0.001)
        assertEquals(155.50, comTransacoes.ativo.precoMedio, 0.001)
        assertEquals(4, comTransacoes.transacoes.size)
    }

    @Test
    fun `excluir ativo remove transacoes associadas em cascata e sincronizar preserva historico`() = runBlocking {
        val itub = Ativo(
            ticker = "ITUB4",
            nome = "Itaú Unibanco PN",
            classe = "ACOES_BR",
            setor = "Bancos",
            quantidade = 100.0,
            precoMedio = 30.0,
            precoAtual = 34.0,
            possuiCotacaoMercado = true
        )
        repository.salvarAtivo(itub)
        dao.inserirTransacao(
            Transacao(
                ativoTicker = "ITUB4",
                tipo = "COMPRA",
                quantidade = 100.0,
                precoUnitario = 30.0,
                valorTotal = 3000.0,
                dataIso = "05/01/2026"
            )
        )

        // Sincronizar atualização de preço/quantidade NÃO deve apagar transações
        repository.sincronizarAtivos(
            listOf(itub.copy(precoAtual = 36.50, quantidade = 120.0))
        )
        assertEquals(1, repository.observarTransacoesDoAtivo("ITUB4").first().size)
        assertEquals(36.50, repository.observarAtivoPorTicker("ITUB4").first()!!.precoAtual, 0.001)

        // Excluir ativo deve disparar ON DELETE CASCADE nas transações
        repository.excluirAtivoPorTicker("ITUB4")
        assertTrue(repository.ativosFlow.first().isEmpty())
        assertTrue(repository.transacoesFlow.first().isEmpty())
    }
}
